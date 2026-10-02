package com.aegis.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private JwtTokenService jwtTokenService;

    @Test
    void authenticatesValidCredentials() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        UserAccount user = new UserAccount("admin", passwordEncoder.encode("correct-password"), Role.ADMIN);
        AuthTokenResponse expected = new AuthTokenResponse("token", "Bearer", Instant.parse("2026-10-02T12:00:00Z"));
        when(userAccountRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtTokenService.issue(user)).thenReturn(expected);

        AuthService authService = new AuthService(userAccountRepository, passwordEncoder, jwtTokenService);

        assertThat(authService.authenticate(new AuthTokenRequest("admin", "correct-password"))).isEqualTo(expected);
    }

    @Test
    void rejectsUnknownOrInvalidCredentialsWithoutRevealingWhichPartFailed() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        when(userAccountRepository.findByUsername("unknown")).thenReturn(Optional.empty());
        AuthService authService = new AuthService(userAccountRepository, passwordEncoder, jwtTokenService);

        assertThatThrownBy(() -> authService.authenticate(new AuthTokenRequest("unknown", "any-password")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid username or password");
    }
}
