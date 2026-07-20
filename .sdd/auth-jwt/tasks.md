# Tasks: auth-service (Registro + Login com JWT)

Quebra de `.sdd/auth-jwt/plan.md` em tasks atômicas. Cada task tem um
critério de aceite testável e independente — implementar fora de ordem
só é seguro respeitando "Depende de".

`[x]` no título = task commitada (todo commit referencia o `TASK-N`
correspondente; `git log --oneline | grep TASK-N` confirma). Marcar ao
concluir cada task, não em lote no fim.

## Scaffold e infraestrutura

### [x] TASK-01 — Scaffold do módulo auth-service
**Depende de:** —
**Descrição:** Criar `auth-service/pom.xml` (Trilha A do
`microservice-scaffold` + `spring-boot-starter-validation` +
`spring-security-crypto` + `jjwt-api`/`jjwt-impl`/`jjwt-jackson` — **não**
`spring-boot-starter-security`), adicionar as 3 dependências `jjwt-*` em
`dependencyManagement` no `pom.xml` raiz (auth-service é o primeiro
módulo a declará-las — skill `spring-security-jwt`), adicionar
`<module>auth-service</module>`, `application.yml` (seção 2 do plan.md
— `jwt.secret` **sem** default, `jwt.expiration-seconds` com default
3600), `Dockerfile`, `CLAUDE.md` local, classe `AuthServiceApplication`.
**Critério de aceite:** `mvn install -pl auth-service -am` compila com
sucesso; subir o serviço **sem** `JWT_SECRET` no ambiente falha o boot
(`ApplicationContext` não sobe) — confirma que não há fallback; com
`JWT_SECRET` setado e `discovery-service` rodando, `AUTH-SERVICE` aparece
registrado em `http://localhost:8761`.

**Verificado nesta task:** `mvn install -pl auth-service -am` compilou;
jar executável confirmado (`Main-Class`/`Start-Class` no manifest); subi
`postgres` + `discovery-service` via Docker e rodei o jar localmente —
Flyway criou o schema `auth` (sem migration ainda), `AUTH-SERVICE`
apareceu `UP` em `http://localhost:8761/eureka/apps/AUTH-SERVICE`.
**Reavaliado após TASK-09 (2026-07-20):** com `JwtTokenGenerator` agora
existindo e lendo `${jwt.secret}` no construtor, refiz a verificação —
subi `postgres` via Docker, rodei o jar (`--eureka.client.enabled=false`)
**sem** `JWT_SECRET` no ambiente: boot falhou com exit code 1,
`BeanCreationException` ao criar `jwtTokenGenerator`, causa raiz
`IllegalArgumentException: Could not resolve placeholder 'JWT_SECRET' in
value "${JWT_SECRET}"` — `ApplicationContext` nunca sobe. A metade "sem
fallback" do critério de aceite agora passa de verdade, não só por
ausência de código que a testasse. Container derrubado ao final.

### [x] TASK-02 — Migration V1: tabelas `users` e `user_roles`
**Depende de:** TASK-01
**Descrição:** `db/migration/V1__create_users.sql` conforme seção 4 do
plan.md — `users` (`id`, `email UNIQUE`, `password_hash`, `created_at`)
e `user_roles` (`user_id` FK, `role`, PK composta `(user_id, role)`).
**Critério de aceite:** com Postgres local rodando, subir o serviço
aplica a migration sem erro; `\d auth.users` e `\d auth.user_roles` no
`psql` mostram as colunas esperadas; `INSERT` manual de dois usuários com
o mesmo `email` falha por violação de `UNIQUE`.

**Verificado nesta task:** subi `postgres` + `discovery-service`, rodei o
jar — log confirma `Migrating schema "auth" to version "1 - create
users"` / `Successfully applied 1 migration`; `\d auth.users` e
`\d auth.user_roles` no `psql` batem exatamente com as colunas e a PK
composta esperadas, incluindo a FK `user_roles.user_id → users.id`;
`INSERT` de dois usuários com o mesmo `email` — o segundo falhou com
`duplicate key value violates unique constraint "users_email_key"`. Linha
de teste removida, containers derrubados ao final. (O volume nomeado
`postgres_data` não é removido por `docker compose down` sem `-v` —
schema `auth` persiste entre sessões de verificação, comportamento
padrão do projeto, não uma pendência desta task.)

## Domain

### [x] TASK-03 — Domain model `User` e enum `Role`
**Depende de:** —
**Descrição:** `record User` e `enum Role` em `domain/model/` conforme
seção 6 do plan.md.
**Critério de aceite:** módulo compila sem nenhum import de
`jakarta.persistence`/`org.springframework`; teste unitário instancia
`User` com `Set.of(Role.ROLE_CUSTOMER)` e verifica os campos (round-trip
trivial).

### [x] TASK-04 — Exceções de domínio
**Depende de:** —
**Descrição:** `InvalidCredentialsException` e
`EmailAlreadyRegisteredException` em `domain/exception/` — `RuntimeException`
simples, sem dependência de framework.
**Critério de aceite:** teste unitário instancia cada exceção e verifica
a mensagem; nenhuma classe importa framework.

## Persistência

### [x] TASK-05 — Entidade JPA `UserEntity`
**Depende de:** TASK-02, TASK-03, TASK-04
**Descrição:** `@Entity @Table("users")` em `adapter/persistence/`,
`@ElementCollection` para `roles` (tabela `user_roles`),
`toDomain()`/`fromDomain()` conforme seção 5 do plan.md. `id` **sem**
`@GeneratedValue` (gerado na usecase — TASK-10).
**Critério de aceite:** teste unitário `fromDomain(user).toDomain()` é
igual ao `user` original, incluindo o conjunto de `roles` (round-trip).

### [x] TASK-06 — Port `UserRepository`
**Depende de:** TASK-03
**Descrição:** interface em `application/port/` (`findByEmail`,
`findById`, `existsByEmail`, `save`) conforme seção 7 do plan.md.
**Critério de aceite:** interface compila sem import de
`jakarta.persistence` nem `org.springframework.data` — só tipos de
domínio.

### [x] TASK-07 — Implementação Spring Data + adapter para `UserRepository`
**Depende de:** TASK-05, TASK-06
**Descrição:** `UserJpaRepository extends JpaRepository<UserEntity, UUID>`
+ `UserRepositoryAdapter` implementando o port; captura
`DataIntegrityViolationException` no `save()` e relança
`EmailAlreadyRegisteredException` (seção 5 do plan.md).
**Critério de aceite:**
(a) teste de integração (Testcontainers Postgres) salva um `User` e
recupera por `findByEmail` com o email normalizado;
(b) teste de integração dispara duas gravações concorrentes com o mesmo
email (duas threads/transações) — exatamente uma tem sucesso, a outra
recebe `EmailAlreadyRegisteredException` (não a
`DataIntegrityViolationException` crua do Spring Data) — reproduz a race
condition apontada no plan.md.

## Segurança (ports + adapters)

### [x] TASK-08 — Port `PasswordHasher` + `BCryptPasswordHasher`
**Depende de:** —
**Descrição:** interface `PasswordHasher` (`hash`, `matches`) em
`application/port/`; implementação `BCryptPasswordHasher` em
`adapter/security/` envolvendo `new BCryptPasswordEncoder(12)`.
**Critério de aceite:** teste unitário — `hash(senha)` nunca é igual à
senha original; `matches(senha, hash(senha))` é `true`;
`matches(senhaErrada, hash)` é `false`; dois `hash()` da mesma senha
produzem valores diferentes (salt).

### [x] TASK-09 — Port `TokenGenerator` + `JwtTokenGenerator`
**Depende de:** TASK-03
**Descrição:** interface `TokenGenerator` (`generate(User): GeneratedToken`)
+ `record GeneratedToken` em `application/port/`; implementação
`JwtTokenGenerator` em `adapter/security/` usando JJWT, HS256, segredo
de `${jwt.secret}`, expiração de `${jwt.expiration-seconds}` (seção 8 do
plan.md).
**Critério de aceite:** teste unitário — token gerado, ao ser parseado de
volta (`Jwts.parser()` com a mesma chave), contém `sub` igual ao id do
usuário, claim `email` igual, claim `roles` igual ao conjunto de roles do
usuário (como array de strings), `exp - iat` igual ao
`expiresInSeconds` configurado; parsear o mesmo token com uma chave
diferente lança exceção de assinatura inválida; **instanciar
`JwtTokenGenerator` com um segredo de menos de 32 caracteres lança
exceção imediatamente no construtor** (não em `generate()`) — teste não
precisa de contexto Spring, só `new JwtTokenGenerator("curto", 3600)`
dentro de um `assertThrows`.

## Usecases

### [ ] TASK-10 — `RegisterUseCase`
**Depende de:** TASK-04, TASK-06, TASK-08
**Critério de aceite (teste unitário com mocks dos ports):**
(a) email é normalizado (`trim().toLowerCase()`) antes de
`existsByEmail` e antes de `save`;
(b) email já existente (mock `existsByEmail=true`) →
`EmailAlreadyRegisteredException`, **sem** chamar `save`;
(c) usuário criado sempre com `roles = Set.of(ROLE_CUSTOMER)` — o
comando de entrada não tem campo role, então não há como o teste
verificar "role ignorado do payload": só que o resultado é sempre
`ROLE_CUSTOMER`;
(d) senha persistida (`passwordHash` do `User` passado a `save`) é o
retorno de `passwordHasher.hash(...)`, nunca a senha em texto puro.

### [ ] TASK-11 — `LoginUseCase`
**Depende de:** TASK-04, TASK-06, TASK-08, TASK-09
**Critério de aceite (teste unitário com mocks):**
(a) email inexistente (`findByEmail` vazio) → `InvalidCredentialsException`;
(b) email existente mas `passwordHasher.matches` retorna `false` → a
**mesma** `InvalidCredentialsException` — teste compara a mensagem desse
caso com a do caso (a) e garante que são idênticas, não só que os dois
casos lançam a mesma classe;
(c) credenciais corretas → chama `tokenGenerator.generate(user)` e o
usecase devolve o `User` + `GeneratedToken` para o controller montar o
`LoginResponse`.

### [ ] TASK-12 — `GetCurrentUserUseCase`
**Depende de:** TASK-06
**Critério de aceite (teste unitário com mock):** `userId` existente →
devolve o `User`; `userId` inexistente → lança (sem exceção de domínio
dedicada — decisão da seção 9 do plan.md: caminho inalcançável nesta
fase, sem endpoint de delete/desativação).

## Web

### [ ] TASK-13 — DTOs com Bean Validation
**Depende de:** TASK-03
**Descrição:** `RegisterRequest` (`@NotBlank @Email` em `email`;
`@NotBlank @Size(min = 8, max = 72)` em `password`), `LoginRequest`
(`@NotBlank` em `email` e `password`, **sem** `@Email`), `UserResponse`,
`LoginResponse` — records em `adapter/web/` conforme seção 10 do
plan.md.
**Critério de aceite:** `RegisterRequest` com `email` malformado falha o
binding (`MethodArgumentNotValidException`); `RegisterRequest` com
`password` de 7 caracteres falha; `LoginRequest` com `email` malformado
(mas não vazio) **passa** a validação — só falha depois no usecase, com
401 (teste confirma que o binding não rejeita, é o `LoginUseCase` quem
trata).

### [ ] TASK-14 — `AuthController` (3 endpoints)
**Depende de:** TASK-10, TASK-11, TASK-12, TASK-13
**Descrição:** `register`, `login`, `me` conforme seção 10 do plan.md —
`register` devolve `UserResponse` com `@ResponseStatus(HttpStatus.CREATED)`,
**sem** `Location` (specify.md — não há `GET /auth/users/{id}`).
**Critério de aceite (teste de integração, `MockMvc`/`WebTestClient`):**
`POST /auth/register` → 201, corpo sem qualquer campo de senha/hash, sem
header `Location`; `POST /auth/login` → 200 com `token`, `expiresIn`,
`id`, `email`, `roles`; `GET /auth/me` com header `X-User-Id` válido →
200 com dados do usuário; `GET /auth/me` **sem** o header → 400.

### [ ] TASK-15 — `GlobalExceptionHandler`
**Depende de:** TASK-04, TASK-14
**Descrição:** `@RestControllerAdvice` mapeando a tabela da seção 10 do
plan.md.
**Critério de aceite:** teste de integração dispara, via chamada HTTP
real, cada cenário da tabela (400 payload malformado, 400 `X-User-Id`
ausente em `/me`, 401 credenciais inválidas, 409 email duplicado) e
verifica que o status retornado bate exatamente com o esperado — um
teste por status, não um teste genérico.

## Fim-a-fim

Os três testes abaixo usam `@SpringBootTest` com contexto completo (como
`ProductIdempotencyHttpFlowTest` no catalog-service) — diferente das
tasks anteriores, esse contexto **instancia `JwtTokenGenerator` de
verdade** e resolve `${jwt.secret}`. Como a seção 2 do plan.md
deliberadamente não dá default a essa propriedade (TASK-01), os três
precisam fixar um valor de teste (≥32 caracteres) via `properties` na
própria anotação — mesmo mecanismo que o catalog-service já usa para
`eureka.client.enabled=false`, não um `application-test.yml` novo:
```java
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "jwt.secret=test-secret-auth-service-minimo-32-caracteres",
        "eureka.client.enabled=false"
    }
)
```
Sem isso, `mvn test -pl auth-service` falharia nesses três testes por
propriedade não resolvida — não por bug de produto. (As tasks anteriores
não precisam disso: `@WebMvcTest`/`@DataJpaTest` não fazem component
scan de `JwtTokenGenerator`, e os testes unitários não sobem contexto
Spring nenhum.)

### [ ] TASK-16 — Fluxo completo register → login → me via HTTP
**Depende de:** TASK-14, TASK-15
**Critério de aceite:** sequência HTTP real — `POST /auth/register`
(201) → `POST /auth/login` com as mesmas credenciais (200, token
retornado) → `GET /auth/me` com `X-User-Id` extraído do `sub` do token
retornado (200, mesmo `email` do registro).

### [ ] TASK-17 — Fluxo de cadastro concorrente com o mesmo email via HTTP
**Depende de:** TASK-14, TASK-15
**Critério de aceite:** dois `POST /auth/register` simultâneos com o
mesmo email — exatamente um retorna 201, o outro retorna 409 (nunca
500) — confirma que a tradução de `DataIntegrityViolationException`
feita no TASK-07 chega intacta até a camada HTTP.

### [ ] TASK-18 — Login sempre com mensagem genérica via HTTP
**Depende de:** TASK-14, TASK-15
**Critério de aceite:** `POST /auth/login` com email inexistente e
`POST /auth/login` com email existente + senha errada retornam o mesmo
status (401) e o mesmo corpo de resposta — teste compara os dois corpos
byte a byte e garante que são idênticos, não só que ambos são 401.
