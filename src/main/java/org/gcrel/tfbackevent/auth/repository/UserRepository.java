package org.gcrel.tfbackevent.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.SystemRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsBySystemRole(SystemRole systemRole);

    // Bloquea las filas del rol, siempre en el mismo orden, para serializar los cambios de
    // rol y evitar que dos degradaciones simultáneas dejen el sistema sin superusuarios.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.systemRole = :role ORDER BY u.id")
    List<User> findBySystemRoleForUpdate(@Param("role") SystemRole role);

    // Evita que un bloqueo y un ascenso del mismo usuario se crucen.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
}
