package br.com.commercehub.auth.adapter.security;

import br.com.commercehub.auth.application.port.GeneratedToken;
import br.com.commercehub.auth.application.port.TokenGenerator;
import br.com.commercehub.auth.domain.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenGenerator implements TokenGenerator {

    private final SecretKey key;
    private final long expirationSeconds;

    /**
     * A {@link SecretKey} é construída aqui, não em {@link #generate}: {@code Keys.hmacShaKeyFor}
     * lança {@code WeakKeyException} para segredos com menos de 256 bits (32 caracteres em
     * UTF-8/ASCII), derrubando o boot do serviço imediatamente em vez de deixá-lo subir saudável
     * e só quebrar no primeiro login real.
     */
    public JwtTokenGenerator(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.expiration-seconds}") long expirationSeconds
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    @Override
    public GeneratedToken generate(User user) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(expirationSeconds);

        String token = Jwts.builder()
            .subject(user.id().toString())
            .claim("email", user.email())
            .claim("roles", user.roles().stream().map(Enum::name).toList())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(key, Jwts.SIG.HS256)
            .compact();

        return new GeneratedToken(token, expirationSeconds);
    }
}
