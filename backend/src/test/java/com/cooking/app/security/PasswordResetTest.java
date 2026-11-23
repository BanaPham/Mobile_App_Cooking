package com.cooking.app.security;

import com.cooking.app.dto.AuthDtos.Forgot;
import com.cooking.app.dto.AuthDtos.Reset;
import com.cooking.app.entity.User;
import com.cooking.app.repository.UserRepository;
import com.cooking.app.service.ApiException;
import com.cooking.app.service.AuthService;
import com.cooking.app.service.MediaService;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordResetTest {
    @Test
    void emailResetCodeCannotLogInAndStopsWorkingAfterPasswordChanges() {
        var users = mock(UserRepository.class);
        var jdbc = mock(JdbcTemplate.class);
        var mail = mock(JavaMailSender.class);
        var passwords = new BCryptPasswordEncoder(4);
        var user = new User();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.email = "tester@example.test";
        user.accountStatus = "ACTIVE";
        user.passwordHash = passwords.encode("OldPass!1");
        when(users.findByEmail(user.email)).thenReturn(Optional.of(user));
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(jdbc.update(anyString(), anyString(), eq(7L), anyString())).thenReturn(1);
        var config = new SecurityConfig();
        var key = config.signingKey(Base64.getEncoder().encodeToString(new byte[32]));
        var tokens = new TokenService(config.jwtEncoder(key), config.jwtDecoder(key), key, users);
        var auth = new AuthService(users, passwords, tokens, jdbc, mail, mock(MediaService.class), "sender@example.test");
        try {
            var originalAccess = tokens.access(user);
            assertEquals("7", tokens.decodeAccess(originalAccess).getSubject());
            auth.forgot(new Forgot(user.email));
            var email = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mail, timeout(2000)).send(email.capture());
            assertArrayEquals(new String[]{user.email}, email.getValue().getTo());
            String[] words = email.getValue().getText().strip().split("\\s+");
            String reset = words[words.length - 1];
            assertThrows(BadJwtException.class, () -> tokens.decodeAccess(reset));
            assertThrows(BadJwtException.class, () -> tokens.resetUser(originalAccess));

            assertEquals("Đã đặt lại mật khẩu", auth.reset(new Reset(reset, "NewPass!2", "NewPass!2")).message());
            assertTrue(passwords.matches("NewPass!2", user.passwordHash));
            assertThrows(ApiException.class, () -> auth.reset(new Reset(reset, "ThirdPass!3", "ThirdPass!3")));
            assertThrows(BadJwtException.class, () -> tokens.decodeAccess(originalAccess));
        } finally {
            ReflectionTestUtils.invokeMethod(auth, "stopMailWorker");
        }
    }
}
