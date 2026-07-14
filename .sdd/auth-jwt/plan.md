# Plan: auth-service (Registro + Login com JWT)

Tradução técnica de `.sdd/auth-jwt/specify.md` para arquitetura concreta.
Segue `microservice-scaffold` (Trilha A) para o esqueleto, `jpa-schema`
para persistência/schema e `spring-security-jwt` para a divisão de
responsabilidade com o api-gateway (ADR 005). Não cobre tasks nem código
— apenas o desenho. O lado api-gateway (filtro de validação, tabela
rota→role) é um plan separado, futuro — fora do escopo deste documento.

## 1. Scaffold do serviço
- Nome: `auth-service`, pacote base `br.com.commercehub.auth`, porta
  `8081` (tabela de portas do `microservice-scaffold`).
- Adicionar `<module>auth-service</module>` no `pom.xml` raiz.
- `pom.xml` filho: dependências padrão da Trilha A (`spring-boot-
  starter-web`, `spring-boot-starter-data-jpa`, `spring-cloud-starter-
  netflix-eureka-client`, `postgresql`, `flyway-core`, `flyway-database-
  postgresql`) **mais**:
  - `spring-boot-starter-validation` (Bean Validation nos DTOs de
    request — mesmo motivo do catalog-service).
  - `spring-security-crypto` — **não** `spring-boot-starter-security`
    completo (decisão do specify.md: o starter completo tranca todas as
    rotas por padrão e exigiria um `SecurityFilterChain` só para reabrir
    `/auth/register`/`/auth/login`; a dependência mínima não traz
    autoconfiguração nenhuma).
  - `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson` — versões
    geridas em `dependencyManagement` no `pom.xml` raiz (ver skill
    `spring-security-jwt`), auth-service é o primeiro módulo a
    declará-las.
- Sem `spring-cloud-starter-openfeign` e sem `spring-kafka`: auth-service
  nunca chama outro serviço de forma síncrona (é sempre o lado chamado,
  ex.: api-gateway confia nele só para emissão — ADR 003/005) e não
  publica/consome eventos nesta fase.

## 2. `application.yml` (schema isolation — skill jpa-schema)
```yaml
spring:
  application:
    name: auth-service
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/commercedb}
    username: ${DB_USER:commerce}
    password: ${DB_PASS:commerce123}
  jpa:
    properties:
      hibernate:
        default_schema: auth
    hibernate:
      ddl-auto: validate
  flyway:
    schemas: auth
    locations: classpath:db/migration

server:
  port: 8081

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka}

jwt:
  secret: ${JWT_SECRET}
  expiration-seconds: ${JWT_EXPIRATION_SECONDS:3600}
```
`jwt.secret` **sem** valor de fallback — propriedade ausente derruba o
boot do serviço (`@Value` sem default falha o contexto), que é o
comportamento desejado pelo specify.md ("segredo sem fallback comitado").
`jwt.expiration-seconds` pode ter default porque não é segredo.

## 3. Estrutura de pacotes (Clean Architecture — CLAUDE.md raiz)
```
auth-service/src/main/java/br/com/commercehub/auth/
├── domain/
│   ├── model/       User (record), Role (enum: ROLE_CUSTOMER, ROLE_ADMIN)
│   └── exception/   InvalidCredentialsException, EmailAlreadyRegisteredException
├── application/
│   ├── port/        UserRepository, PasswordHasher, TokenGenerator, GeneratedToken
│   └── usecase/      RegisterUseCase, LoginUseCase, GetCurrentUserUseCase
└── adapter/
    ├── security/    BCryptPasswordHasher, JwtTokenGenerator
    ├── persistence/ UserEntity, UserJpaRepository, UserRepositoryAdapter
    └── web/         AuthController, DTOs (records), GlobalExceptionHandler
```
Sem `adapter/messaging/` (sem eventos). `adapter/security/` é a mesma
extensão que a skill `spring-security-jwt` já introduziu — port para lib
externa (BCrypt/JJWT), mesmo espírito do `IdempotencyKeyStore` do
catalog-service.

## 4. Migrations Flyway (`src/main/resources/db/migration/`)
```
V1__create_users.sql
```
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, role)
);
```
`UNIQUE` em `email` é a fonte de verdade da unicidade (specify.md — não
só checagem na usecase). `user_roles` é uma tabela filha simples (sem PK
própria; `(user_id, role)` compõe a chave) para mapear `Set<Role>` via
`@ElementCollection` — não uma FK para uma tabela `roles` separada,
porque `Role` é um enum fechado de 2 valores, não uma entidade com vida
própria. Sem `updated_at`/`version`: nenhum endpoint de update nesta
fase (specify.md).

## 5. Entidade JPA (`adapter/persistence/`)
```java
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private Set<Role> roles;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    // toDomain() / fromDomain(User) — mesmo padrão de ProductEntity
}
```
`id` **não** usa `@GeneratedValue` — gerado em `RegisterUseCase` via
`UUID.randomUUID()` antes de persistir, mesmo padrão de
`CreateProductUseCase` (o usecase, não o banco, decide o id). `roles`
`EAGER` porque toda leitura de `User` no domínio já precisa das roles
(claim do JWT, resposta do `/me`) — não há caso de uso que carregue
`User` sem elas.

`UserRepositoryAdapter.save()` captura
`DataIntegrityViolationException` (violação da constraint `UNIQUE` em
`email`) e relança como `EmailAlreadyRegisteredException` — cobre a
janela de corrida entre o `existsByEmail` da usecase e o `INSERT`: sob
duas requisições concorrentes com o mesmo email, é a constraint do banco
quem decide, e o adapter garante que o chamador sempre vê a exceção de
domínio, nunca a exceção genérica do Spring Data.

## 6. Domain model (`domain/model/` — sem JPA)
```java
public enum Role { ROLE_CUSTOMER, ROLE_ADMIN }

public record User(
    UUID id, String email, String passwordHash, Set<Role> roles, OffsetDateTime createdAt
) {}
```

## 7. Ports (`application/port/`)
```java
public interface UserRepository {
    Optional<User> findByEmail(String email);
    Optional<User> findById(UUID id);
    boolean existsByEmail(String email);
    User save(User user);
}

public interface PasswordHasher {
    String hash(String rawPassword);
    boolean matches(String rawPassword, String passwordHash);
}

public interface TokenGenerator {
    GeneratedToken generate(User user);
}

public record GeneratedToken(String token, long expiresInSeconds) {}
```
`TokenGenerator` devolve `expiresInSeconds` junto com o token — não
`long` isolado no usecase lido de outra config — porque só o adapter
(`JwtTokenGenerator`) precisa saber de `jwt.expiration-seconds`; o
usecase só repassa o valor para o `LoginResponse`, sem duplicar a
propriedade em dois lugares.

## 8. Adapters de segurança (`adapter/security/`)
- `BCryptPasswordHasher implements PasswordHasher` — envolve
  `new BCryptPasswordEncoder(12)` (`spring-security-crypto`, strength 12
  — regra global do CLAUDE.md raiz).
- `JwtTokenGenerator implements TokenGenerator` — `secret` injetado de
  `${jwt.secret}` via `@Value` (sem default, seção 2) e a `SecretKey`
  **construída uma única vez, no construtor**, via
  `Keys.hmacShaKeyFor(secret.getBytes(UTF_8))` — não dentro de
  `generate()`. `generate()` só usa a chave já pronta:
  `Jwts.builder().subject(id).claim("email", email).claim("roles",
  roles).issuedAt(now).expiration(now + expirationSeconds).signWith(key,
  Jwts.SIG.HS256).compact()`. **Nota operacional:** HS256 exige chave de
  no mínimo 256 bits — `JWT_SECRET` precisa ter pelo menos 32 caracteres
  (assumindo UTF-8/ASCII, 1 byte por caractere). Construir a chave no
  construtor (não sob demanda) é o que garante que um segredo curto
  demais derruba o boot do serviço — mesma categoria de falha que a
  ausência total de `JWT_SECRET` já causa (seção 2) — em vez de deixar o
  serviço subir saudável e só quebrar (500) no primeiro login real.

## 9. Usecases (`application/usecase/`)
| Usecase | Regra |
|---|---|
| `RegisterUseCase` | normaliza email (`trim().toLowerCase()`); `existsByEmail` → `EmailAlreadyRegisteredException`→409 se true; `passwordHasher.hash(rawPassword)`; monta `User` com `id=UUID.randomUUID()`, `roles=Set.of(ROLE_CUSTOMER)` sempre (payload não tem campo role — nada a ler), `createdAt=now`; `userRepository.save(user)`; devolve o `User` criado |
| `LoginUseCase` | normaliza email; `findByEmail` — vazio → `InvalidCredentialsException`→401; `passwordHasher.matches(rawPassword, user.passwordHash())` — false → **a mesma** `InvalidCredentialsException`→401 (specify.md: mensagem genérica idêntica nos dois casos, não dois `throw` com mensagens diferentes); `tokenGenerator.generate(user)` → `GeneratedToken`; devolve `user` + `GeneratedToken` para o controller montar o `LoginResponse` |
| `GetCurrentUserUseCase` | recebe `UUID userId` (controller extrai do header `X-User-Id`); `findById(userId).orElseThrow(...)`. Não há exceção de domínio dedicada para esse caminho: sem endpoint de delete/desativação nesta fase (specify.md), um `X-User-Id` que não resolve para um usuário existente não é um cenário alcançável — o gateway só emite esse header a partir de um token que o próprio auth-service assinou para um usuário que existia no momento do login. Deixar `orElseThrow` estourar sem exceção de domínio própria é intencional, não uma lacuna: não há regra de negócio real para cobrir. |

Nenhum dos três chama `TokenGenerator`/`PasswordHasher` diretamente por
import de lib — sempre via port (seção 7), mesmo raciocínio do
`IdempotencyKeyStore` em catalog-service (o `arch-reviewer` não
bloquearia o atalho, mas a convenção do projeto é não abrir a exceção).

## 10. Adapter web (`adapter/web/`)
```java
@RestController
@RequestMapping("/auth")
public class AuthController {

    @PostMapping("/register")   // 201, sem Location (specify.md — não há GET /auth/users/{id})
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) { ... }

    @PostMapping("/login")      // 200
    public LoginResponse login(@Valid @RequestBody LoginRequest request) { ... }

    @GetMapping("/me")          // 200
    public UserResponse me(@RequestHeader("X-User-Id") UUID userId) { ... }
}
```
DTOs (records, `adapter/web/`):
- `RegisterRequest(String email, String password)` — `@NotBlank @Email`
  em `email`; `@NotBlank @Size(min = 8, max = 72)` em `password`,
  contando **caracteres** (`String.length()`), não bytes — gap
  documentado e aceito explicitamente em `specify.md` → Fora do escopo
  (senha multi-byte pode passar na validação e ainda ser truncada pelo
  BCrypt, que trunca em 72 *bytes*).
- `LoginRequest(String email, String password)` — **só** `@NotBlank`
  nos dois campos, sem `@Email`/`@Size`: um formato de email inválido no
  login não deve virar 400 diferenciado de "email não encontrado" (isso
  vazaria informação); ele simplesmente não bate no `findByEmail` e cai
  no mesmo 401 genérico.
- `UserResponse(UUID id, String email, Set<Role> roles, OffsetDateTime createdAt)`
  — usado tanto no 201 do register quanto no 200 do `/me`; nunca inclui
  `passwordHash`.
- `LoginResponse(String token, long expiresIn, UUID id, String email, Set<Role> roles)`.

`GlobalExceptionHandler` — mesmo padrão de `ErrorResponse` do
catalog-service:

| Exceção capturada | HTTP |
|---|---|
| `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, `MissingRequestHeaderException` (`X-User-Id` ausente em `/me`) | 400 |
| `InvalidCredentialsException` | 401 |
| `EmailAlreadyRegisteredException` | 409 |

## 11. Fora do escopo (herdado do specify.md)
Refresh token, logout/blacklist, conta desabilitada, endpoint de
promoção a admin (primeiro `ROLE_ADMIN` via seed SQL manual, fora deste
plan também), alteração/recuperação de senha, atualização de perfil,
rate limiting, `spring-boot-starter-security`/`SecurityFilterChain` em
auth-service, e qualquer validação de JWT dentro do próprio auth-service
— quem valida é sempre o api-gateway (ADR 005), em um plan futuro
separado.
