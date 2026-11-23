package com.cooking.app.security;

import com.cooking.app.entity.User;
import com.cooking.app.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final SecretKey key;
    private final UserRepository users;

    public TokenService(JwtEncoder encoder, JwtDecoder decoder, SecretKey key, UserRepository users) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.key = key;
        this.users = users;
    }

    public String access(User user) {
        return issue(user, "access", Duration.ofDays(7));
    }

    public String reset(User user) {
        return issue(user, "reset", Duration.ofMinutes(15));
    }

    public Jwt decodeAccess(String value) {
        Jwt jwt = decode(value, "access");
        validateUser(jwt);
        return jwt;
    }

    public User resetUser(String value) {
        return validateUser(decode(value.trim(), "reset"));
    }

    private String issue(User user, String purpose, Duration duration) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("bep-nha").subject(user.getId().toString())
            .issuedAt(now).expiresAt(now.plus(duration))
            .claim("purpose", purpose).claim("version", fingerprint(user, purpose)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    }

    private Jwt decode(String token, String purpose) {
        Jwt jwt = decoder.decode(token);
        if (!"bep-nha".equals(jwt.getClaimAsString("iss")) || !purpose.equals(jwt.getClaimAsString("purpose"))
            || jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(Instant.now())) {
            throw new BadJwtException("Mã xác thực không hợp lệ");
        }
        return jwt;
    }

    private User validateUser(Jwt jwt) {
        User user;
        try {
            user = users.findById(Long.parseLong(jwt.getSubject())).orElseThrow(
                () -> new BadJwtException("Tài khoản không hợp lệ"));
        } catch (NumberFormatException exception) {
            throw new BadJwtException("Tài khoản không hợp lệ", exception);
        }
        String version = jwt.getClaimAsString("version");
        if (!"ACTIVE".equals(user.accountStatus) || version == null || !MessageDigest.isEqual(
            version.getBytes(StandardCharsets.UTF_8), fingerprint(user, jwt.getClaimAsString("purpose")).getBytes(StandardCharsets.UTF_8))) {
            throw new BadJwtException("Phiên đã hết hiệu lực");
        }
        return user;
    }

    private String fingerprint(User user, String purpose) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            String value = "password-version:" + purpose + ":" + user.getId() + ":" + user.passwordHash
                + ("reset".equals(purpose) ? ":" + user.email : "");
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
