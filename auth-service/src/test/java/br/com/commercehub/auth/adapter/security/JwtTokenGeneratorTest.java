package br.com.commercehub.auth.adapter.security;

import br.com.commercehub.auth.application.port.GeneratedToken;
import br.com.commercehub.auth.domain.model.Role;
import br.com.commercehub.auth.domain.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenGeneratorTest {

    private static final String SECRET = "test-secret-jwt-token-generator-32-chars-minimum";
    private static final long EXPIRATION_SECONDS = 3600;

    private final JwtTokenGenerator generator = new JwtTokenGenerator(SECRET, EXPIRATION_SECONDS);

    @Test
    void generatesTokenWithExpectedClaims() {
        User user = new User(
            UUID.randomUUID(), "user@example.com", "hashed-password",
            Set.of(Role.ROLE_CUSTOMER, Role.ROLE_ADMIN), OffsetDateTime.now()
        );

        GeneratedToken generated = generator.generate(user);

        Claims claims = parseWithKey(generated.token(), SECRET);

        assertThat(claims.getSubject()).isEqualTo(user.id().toString());
        assertThat(claims.get("email", String.class)).isEqualTo(user.email());
        assertThat(claims.get("roles", List.class)).containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_ADMIN");

        long actualDurationSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(actualDurationSeconds).isEqualTo(EXPIRATION_SECONDS);
        assertThat(generated.expiresInSeconds()).isEqualTo(EXPIRATION_SECONDS);
    }

    @Test
    void parsingWithDifferentKeyThrowsSignatureException() {
        User user = new User(
            UUID.randomUUID(), "user@example.com", "hashed-password",
            Set.of(Role.ROLE_CUSTOMER), OffsetDateTime.now()
        );
        GeneratedToken generated = generator.generate(user);
        String differentSecret = "different-secret-also-32-characters-min";

        assertThrows(SignatureException.class, () -> parseWithKey(generated.token(), differentSecret));
    }

    @Test
    void constructorRejectsSecretShorterThan32Characters() {
        assertThrows(WeakKeyException.class, () -> new JwtTokenGenerator("curto", 3600));
    }

    private Claims parseWithKey(String token, String secret) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
