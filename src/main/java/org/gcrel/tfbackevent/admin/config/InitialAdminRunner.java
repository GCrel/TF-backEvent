package org.gcrel.tfbackevent.admin.config;

import java.time.LocalDate;

import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.auth.service.EmailNormalizer;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.SystemRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

// Crea el primer superusuario desde variables de entorno, solo si todavía no existe ninguno:
// así no hay contraseñas en el repositorio ni en el seed de dev.
@Slf4j
@Component
public class InitialAdminRunner implements ApplicationRunner {
    private static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD = 72;
    // La columna es obligatoria; se puede corregir luego desde la cuenta.
    private static final LocalDate PLACEHOLDER_BIRTH_DATE = LocalDate.of(1970, 1, 1);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String name;

    public InitialAdminRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${admin.initial.email:}") String email,
            @Value("${admin.initial.password:}") String password,
            @Value("${admin.initial.name:Administrador}") String name) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.name = name;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsBySystemRole(SystemRole.ADMIN)) {
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            log.warn(
                    "No hay ningún superusuario. Definí INITIAL_ADMIN_EMAIL e INITIAL_ADMIN_PASSWORD para crear el primero.");
            return;
        }
        if (password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            log.error("INITIAL_ADMIN_PASSWORD debe tener entre {} y {} caracteres; no se creó el superusuario.",
                    MIN_PASSWORD, MAX_PASSWORD);
            return;
        }
        String normalizedEmail = EmailNormalizer.normalize(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            log.error("Ya existe un usuario con el email de INITIAL_ADMIN_EMAIL; no se lo ascendió automáticamente.");
            return;
        }

        User admin = new User(name, normalizedEmail, passwordEncoder.encode(password), PLACEHOLDER_BIRTH_DATE);
        admin.setSystemRole(SystemRole.ADMIN);
        admin.setEnabled(true);
        userRepository.save(admin);
        log.info("Superusuario inicial creado: {}", normalizedEmail);
    }
}
