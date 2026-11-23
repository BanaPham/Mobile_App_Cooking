package com.cooking.app.service;

import com.cooking.app.dto.AuthDtos.*;
import com.cooking.app.entity.User;
import com.cooking.app.repository.UserRepository;
import com.cooking.app.security.TokenService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final JdbcTemplate sql;
    private final JavaMailSender mail;
    private final MediaService media;
    private final String from;
    private final String dummyHash;
    private final ConcurrentHashMap<String, Instant> recentResetRequests = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor emailWorker = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(32), new ThreadPoolExecutor.DiscardPolicy());

    public AuthService(UserRepository users, PasswordEncoder passwords, TokenService tokens, JdbcTemplate sql,
                       JavaMailSender mail, MediaService media, @Value("${app.mail-from}") String from) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.sql = sql;
        this.mail = mail;
        this.media = media;
        this.from = from;
        this.dummyHash = passwords.encode("this-password-is-not-a-user-password");
    }

    @jakarta.annotation.PreDestroy
    void stopMailWorker() {
        emailWorker.shutdownNow();
    }

    public Message register(Register input) {
        String name = Input.text(input.fullName(), "họ và tên", 120);
        String email = Input.email(input.email());
        String phone = Input.phone(input.phone());
        Input.password(input.password(), input.confirmation());
        checkDuplicates(email, phone, null);
        User user = new User();
        user.fullName = name;
        user.email = email;
        user.phone = phone;
        user.passwordHash = passwords.encode(input.password());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            checkDuplicates(email, phone, null);
            throw exception;
        }
        return new Message("Đã tạo tài khoản thành công");
    }

    public Session login(Login input) {
        String identifier = Input.text(input.identifier(), "email hoặc số điện thoại", 254);
        Optional<User> result;
        if (identifier.contains("@")) {
            result = users.findByEmail(identifier.toLowerCase(Locale.ROOT));
        } else {
            try {
                result = users.findByPhone(Input.phone(identifier));
            } catch (ApiException exception) {
                result = Optional.empty();
            }
        }
        User user = result.orElse(null);
        String password = input.password();
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72
            || !passwords.matches(password, user == null ? dummyHash : user.passwordHash)
            || user == null || !"ACTIVE".equals(user.accountStatus)) {
            throw ApiException.denied("Sai tên đăng nhập hoặc mật khẩu");
        }
        return new Session(tokens.access(user));
    }

    public void updateProfile(long id, ProfileUpdate input) {
        String email = Input.email(input.email());
        String phone = Input.phone(input.phone());
        String name = Input.text(input.fullName(), "họ và tên", 120);
        String bio = Input.optional(input.bio(), "Tiểu sử", 160);
        String avatar = Input.optional(input.avatarUrl(), "Ảnh đại diện", 2048);
        if (avatar != null && avatar.startsWith("/api/media/")) media.validateOwner(avatar, id);
        else avatar = Input.httpsUrl(avatar, "Ảnh đại diện");
        checkDuplicates(email, phone, id);
        try {
            sql.update("""
                UPDATE Users SET email=?, phone=?, full_name=?, bio=?, avatar_url=?, updated_at=SYSDATETIME()
                WHERE user_id=?
                """, email, phone, name, bio, avatar, id);
        } catch (DataIntegrityViolationException exception) {
            checkDuplicates(email, phone, id);
            throw exception;
        }
    }

    public Session change(long id, PasswordChange input) {
        User user = users.findById(id).orElseThrow(() -> ApiException.denied("Vui lòng đăng nhập lại"));
        if (input.currentPassword() == null || input.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72
            || !passwords.matches(input.currentPassword(), user.passwordHash)) {
            throw ApiException.bad("Mật khẩu hiện tại không đúng");
        }
        replace(user, input.password(), input.confirmation());
        return new Session(tokens.access(user));
    }

    public Message forgot(Forgot input) {
        String email = Input.email(input.email());
        if (from.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Máy chủ chưa cấu hình gửi email");
        }
        Instant now = Instant.now();
        recentResetRequests.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        if (recentResetRequests.size() < 1000
            && recentResetRequests.putIfAbsent(email, now.plus(Duration.ofMinutes(1))) == null) {
            emailWorker.execute(() -> users.findByEmail(email).filter(user -> "ACTIVE".equals(user.accountStatus))
                .ifPresent(this::sendReset));
        }
        return new Message("Nếu email đã đăng ký, bạn sẽ nhận được mã đặt lại mật khẩu.");
    }

    public Message reset(Reset input) {
        User user;
        try {
            user = tokens.resetUser(Input.text(input.token(), "mã đặt lại", 2048));
        } catch (JwtException exception) {
            throw ApiException.bad("Mã đặt lại không hợp lệ hoặc đã hết hạn");
        }
        replace(user, input.password(), input.confirmation());
        return new Message("Đã đặt lại mật khẩu");
    }

    private void replace(User user, String password, String confirmation) {
        Input.password(password, confirmation);
        String encoded = passwords.encode(password);
        int changed = sql.update("""
            UPDATE Users SET password_hash=?, updated_at=SYSDATETIME()
            WHERE user_id=? AND password_hash COLLATE Latin1_General_100_BIN2=?
            """, encoded, user.getId(), user.passwordHash);
        if (changed != 1) throw ApiException.bad("Phiên đã hết hiệu lực. Vui lòng thử lại.");
        user.passwordHash = encoded;
    }

    private void checkDuplicates(String email, String phone, Long id) {
        if (users.findByEmail(email).filter(found -> !found.getId().equals(id)).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, "Email này đã được đăng kí trước đó");
        if (users.findByPhone(phone).filter(found -> !found.getId().equals(id)).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, "Số điện thoại này đã được đăng kí trước đó");
    }

    private void sendReset(User user) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.email);
        message.setSubject("Bếp Nhà — Đặt lại mật khẩu");
        message.setText("Dán mã sau vào màn Quên mật khẩu của Bếp Nhà. Mã hết hạn sau 15 phút.\n\n"
            + tokens.reset(user));
        try {
            mail.send(message);
        } catch (MailException exception) {
            LOG.warn("Gửi email đặt lại mật khẩu thất bại ({})", exception.getClass().getSimpleName());
        }
    }
}
