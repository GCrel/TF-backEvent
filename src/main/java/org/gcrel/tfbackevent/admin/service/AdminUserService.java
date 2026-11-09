package org.gcrel.tfbackevent.admin.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.gcrel.tfbackevent.admin.dto.response.UserAdminResponse;
import org.gcrel.tfbackevent.admin.exception.AdminConflictException;
import org.gcrel.tfbackevent.admin.exception.UserNotFoundException;
import org.gcrel.tfbackevent.auth.exception.IncorrectPasswordException;
import org.gcrel.tfbackevent.auth.repository.RefreshTokenRepository;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.auth.service.EmailService;
import org.gcrel.tfbackevent.common.exception.BadRequestException;
import org.gcrel.tfbackevent.common.exception.ForbiddenException;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.AuditAction;
import org.gcrel.tfbackevent.model.enums.AuditTargetType;
import org.gcrel.tfbackevent.model.enums.SystemRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditService auditService;

    public AdminUserService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<UserAdminResponse> search(String query, SystemRole role, Boolean blocked, Pageable pageable) {
        Specification<User> spec = Specification.unrestricted();
        if (query != null && !query.isBlank()) {
            // Se escapan los comodines de LIKE para que '%' y '_' se busquen literalmente.
            String pattern = "%" + query.strip().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern, '\\'),
                    cb.like(cb.lower(root.get("email")), pattern, '\\')));
        }
        if (role != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("systemRole"), role));
        }
        if (blocked != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("blocked"), blocked));
        }
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by("name", "id"));
        return userRepository.findAll(spec, page).map(UserAdminResponse::new);
    }

    @Transactional(readOnly = true)
    public UserAdminResponse get(UUID id) {
        return new UserAdminResponse(userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id)));
    }

    @Transactional
    public UserAdminResponse block(User actor, UUID id, String reason) {
        User target = lockTarget(id);
        if (target.getId().equals(actor.getId())) {
            throw new BadRequestException("No podés bloquearte a vos mismo.");
        }
        // A un superusuario se le quita el rol primero: así no se bloquean entre sí.
        if (target.getSystemRole() == SystemRole.ADMIN) {
            throw new AdminConflictException("Un superusuario no puede bloquearse: primero quitale el rol.");
        }
        if (target.isBlocked()) {
            throw new AdminConflictException("El usuario ya está bloqueado.");
        }
        target.block(reason.strip(), LocalDateTime.now());
        userRepository.save(target);
        refreshTokenRepository.deleteAllByUser(target);
        auditService.record(actor, AuditAction.USER_BLOCKED, AuditTargetType.USER, id, reason.strip());
        emailService.sendNotice(target.getEmail(), "Tu cuenta fue bloqueada",
                "Un administrador bloqueó tu cuenta.\nMotivo: " + reason.strip());
        return new UserAdminResponse(target);
    }

    @Transactional
    public UserAdminResponse unblock(User actor, UUID id) {
        User target = lockTarget(id);
        if (!target.isBlocked()) {
            throw new AdminConflictException("El usuario no está bloqueado.");
        }
        target.unblock();
        userRepository.save(target);
        auditService.record(actor, AuditAction.USER_UNBLOCKED, AuditTargetType.USER, id, null);
        emailService.sendNotice(target.getEmail(), "Tu cuenta fue desbloqueada",
                "Un administrador desbloqueó tu cuenta. Ya podés iniciar sesión.");
        return new UserAdminResponse(target);
    }

    // Borra los refresh tokens: el access token vigente dura hasta 30 minutos. Para cortar de
    // inmediato está el bloqueo.
    @Transactional
    public void revokeSessions(User actor, UUID id) {
        User target = lockTarget(id);
        refreshTokenRepository.deleteAllByUser(target);
        auditService.record(actor, AuditAction.USER_SESSIONS_REVOKED, AuditTargetType.USER, id, null);
        emailService.sendNotice(target.getEmail(), "Se cerraron tus sesiones",
                "Un administrador cerró todas tus sesiones. Tendrás que iniciar sesión de nuevo.");
    }

    @Transactional
    public UserAdminResponse changeRole(User actor, UUID id, SystemRole role, String password) {
        // Se bloquean todos los superusuarios antes de decidir: dos cambios simultáneos se
        // serializan y el segundo ve el estado ya modificado por el primero.
        List<User> admins = userRepository.findBySystemRoleForUpdate(SystemRole.ADMIN);
        User currentActor = admins.stream()
                .filter(admin -> admin.getId().equals(actor.getId()))
                .findFirst()
                .orElseThrow(() -> new ForbiddenException("Ya no sos superusuario."));
        if (!passwordEncoder.matches(password, currentActor.getPassword())) {
            throw new IncorrectPasswordException();
        }
        if (id.equals(actor.getId())) {
            throw new BadRequestException("No podés cambiar tu propio rol.");
        }

        User target = lockTarget(id);
        if (target.getSystemRole() == role) {
            throw new AdminConflictException("El usuario ya tiene el rol " + role + ".");
        }
        if (role == SystemRole.ADMIN) {
            if (!target.isEnabled() || target.isBlocked()) {
                throw new AdminConflictException("El usuario debe estar verificado y no bloqueado.");
            }
        } else if (admins.size() <= 1) {
            throw new AdminConflictException("No puede quedar el sistema sin superusuarios.");
        }

        target.setSystemRole(role);
        userRepository.save(target);
        refreshTokenRepository.deleteAllByUser(target);
        boolean granted = role == SystemRole.ADMIN;
        auditService.record(actor, granted ? AuditAction.ROLE_GRANTED : AuditAction.ROLE_REVOKED,
                AuditTargetType.USER, id, "Rol: " + role);
        emailService.sendNotice(target.getEmail(),
                granted ? "Ahora sos superusuario" : "Ya no sos superusuario",
                granted ? "Un administrador te asignó el rol de superusuario. Tus sesiones se cerraron: iniciá sesión de nuevo."
                        : "Un administrador te quitó el rol de superusuario. Tus sesiones se cerraron: iniciá sesión de nuevo.");
        return new UserAdminResponse(target);
    }

    private User lockTarget(UUID id) {
        return userRepository.findByIdForUpdate(id).orElseThrow(() -> new UserNotFoundException(id));
    }
}
