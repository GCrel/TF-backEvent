package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.gcrel.tfbackevent.admin.config.InitialAdminRunner;
import org.gcrel.tfbackevent.auth.repository.UserRepository;
import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.model.enums.SystemRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class InitialAdminRunnerTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private InitialAdminRunner runner(String email, String password) {
        return new InitialAdminRunner(userRepository, passwordEncoder, email, password, "Administrador");
    }

    @Test
    void createsTheFirstSuperuserFromTheEnvironment() {
        when(userRepository.existsBySystemRole(SystemRole.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail("root@example.com")).thenReturn(false);
        when(passwordEncoder.encode("clave-larga")).thenReturn("hash");

        runner("  ROOT@Example.com ", "clave-larga").run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User admin = captor.getValue();
        assertEquals("root@example.com", admin.getEmail());
        assertEquals("hash", admin.getPassword());
        assertEquals(SystemRole.ADMIN, admin.getSystemRole());
        assertTrue(admin.isEnabled());
    }

    @Test
    void doesNothingWhenASuperuserAlreadyExists() {
        when(userRepository.existsBySystemRole(SystemRole.ADMIN)).thenReturn(true);
        runner("root@example.com", "clave-larga").run(null);
        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNothingWithoutCredentials() {
        when(userRepository.existsBySystemRole(SystemRole.ADMIN)).thenReturn(false);
        runner("", "").run(null);
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsAShortPassword() {
        when(userRepository.existsBySystemRole(SystemRole.ADMIN)).thenReturn(false);
        runner("root@example.com", "corta").run(null);
        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNotPromoteAnExistingUserWithThatEmail() {
        when(userRepository.existsBySystemRole(SystemRole.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail("root@example.com")).thenReturn(true);
        runner("root@example.com", "clave-larga").run(null);
        verify(userRepository, never()).save(any());
    }
}
