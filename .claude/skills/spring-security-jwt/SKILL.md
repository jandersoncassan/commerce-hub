---
name: spring-security-jwt
description: Use esta skill sempre que for implementar emissão ou validação
  de JWT, configurar Spring Security, ou proteger rotas em auth-service ou
  api-gateway. Cobre os dois lados: auth-service emite (hexagonal
  completo), api-gateway valida e autoriza por rota (serviço de
  infraestrutura, sem domain/application/adapter — ver ADR 004). A
  centralização da autenticação no gateway e a propagação de identidade
  via headers são decisão estrutural do ADR 005, não desta skill. Os
  demais serviços de negócio NÃO usam esta skill: eles confiam no gateway
  e não levam dependência de segurança própria (ADR 005). Assume que
  auth-service já existe como módulo — para criá-lo do zero use primeiro
  microservice-scaffold (Trilha A) e, para a tabela users, jpa-schema.
---

## Divisão de responsabilidade

| Serviço | Papel |
|---|---|
| auth-service | Único emissor de JWT. Hexagonal completo (Trilha A). |
| api-gateway | Único validador de assinatura/expiração e único ponto de autorização por rota/role (ADR 005). Trilha B, sem hexagonal. |
| demais 8 serviços | Não fazem nada de segurança. Não têm `spring-security` nem lib JWT como dependência. Só leem `X-User-Id`/`X-User-Roles` quando precisam de identidade para uma regra de negócio (ADR 005). |

Ver `ADR 005` para o raciocínio completo por trás dessa divisão — esta
skill não redefine a decisão, só mostra onde cada peça mora no código.

## Dependências a adicionar

`pom.xml` raiz — `dependencyManagement`, no mesmo bloco onde já estão
`spring-boot-dependencies`/`spring-cloud-dependencies` (o BOM do Spring
Boot já gerencia a versão de `spring-boot-starter-security`; só a lib JWT
precisa de versão explícita, igual ao padrão já usado pelo projeto para
libs fora do BOM):

```xml
<dependencyManagement>
  <dependencies>
    <!-- ... spring-boot-dependencies, spring-cloud-dependencies já existentes ... -->
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-api</artifactId>
      <version>0.12.6</version>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-impl</artifactId>
      <version>0.12.6</version>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-jackson</artifactId>
      <version>0.12.6</version>
    </dependency>
  </dependencies>
</dependencyManagement>
```

- **auth-service** declara `spring-boot-starter-security` +
  `jjwt-api`/`jjwt-impl`/`jjwt-jackson` (além de `spring-boot-starter-
  data-jpa`, que já é padrão de serviço de negócio — ver
  `microservice-scaffold`).
- **api-gateway** declara só `jjwt-api`/`jjwt-impl`/`jjwt-jackson` — não
  precisa de `spring-boot-starter-security` completo, porque a validação
  acontece num `GlobalFilter` próprio do Spring Cloud Gateway (WebFlux),
  não via `SecurityFilterChain` do Spring MVC. Trazer o starter inteiro
  para um serviço reativo é peso desnecessário para o que o gateway
  precisa fazer aqui.
- Os demais 8 serviços não declaram nenhuma dessas dependências (ADR 005).

## auth-service — onde cada peça mora (hexagonal, Trilha A)

```
auth-service/src/main/java/br/com/commercehub/auth/
├── domain/
│   ├── model/
│   │   └── User.java                    ← record: id, email, passwordHash, roles, createdAt
│   └── exception/
│       ├── InvalidCredentialsException.java
│       └── EmailAlreadyRegisteredException.java
├── application/
│   ├── port/
│   │   ├── UserRepository.java
│   │   ├── PasswordHasher.java
│   │   └── TokenGenerator.java
│   └── usecase/
│       ├── RegisterUseCase.java         ← orquestra UserRepository + PasswordHasher
│       └── LoginUseCase.java            ← orquestra UserRepository + PasswordHasher + TokenGenerator
└── adapter/
    ├── security/
    │   ├── BCryptPasswordHasher.java    ← implements PasswordHasher, BCrypt strength 12
    │   └── JwtTokenGenerator.java       ← implements TokenGenerator, HS256
    ├── persistence/
    │   ├── UserEntity.java              ← schema `auth` (ADR 002)
    │   ├── UserJpaRepository.java
    │   └── UserRepositoryAdapter.java   ← implements UserRepository
    └── web/
        ├── AuthController.java          ← POST /auth/register, POST /auth/login
        ├── RegisterRequest.java / LoginRequest.java / TokenResponse.java  ← DTOs record
        └── GlobalExceptionHandler.java  ← mesmo padrão de catalog-service
```

`domain/model/User` e `domain/exception/*` seguem a mesma regra de pureza
que `arch-reviewer` já audita em outros serviços: zero import de
`jakarta`, `org.springframework` ou `lombok`.

`PasswordHasher` e `TokenGenerator` existem como interfaces em
`application/port/` pelo mesmo motivo que `IdempotencyKeyStore` existe em
catalog-service: usecases não devem depender de biblioteca externa
diretamente, só de ports. O `arch-reviewer` atual não bloquearia um
`PasswordEncoder`/jjwt importado direto dentro de `application/usecase/`
(seus greps cobrem `jakarta.persistence` em usecase, não
`org.springframework.security` ou `io.jsonwebtoken`) — mas seguir o
mesmo padrão de porta já estabelecido no código evita introduzir uma
exceção silenciosa à convenção.

`GlobalExceptionHandler` mapeia:
- `InvalidCredentialsException` → 401
- `EmailAlreadyRegisteredException` → 409

Reaproveita o mesmo `ErrorResponse` record (`status`, `message`,
`timestamp`) já usado em catalog-service — não recriar um formato de erro
diferente.

### Persistência (jpa-schema)

`UserEntity` fica no schema `auth` (ver ADR 002), com migration Flyway
própria (`V1__create_users.sql`) seguindo os tipos padrão do projeto: `id
UUID`, datas `TIMESTAMP WITH TIME ZONE`. Roles podem ser uma coluna
`TEXT[]`/tabela separada `user_roles` — decisão de modelagem local, mas
sempre com `ddl-auto: validate` e a mudança versionada via migration,
nunca `create`/`update` (ver skill `jpa-schema`).

### Segredo JWT

`${JWT_SECRET}` — **sem default hardcoded** no `application.yml` (ex.:
`${JWT_SECRET}`, não `${JWT_SECRET:algum-valor}`). Isso é diferente do
padrão usado para credenciais de banco (`${DB_PASS:commerce123}`), porque
credencial de dev local pode ter fallback comitado, mas segredo de
assinatura de token não — se `JWT_SECRET` não estiver setado, o serviço
deve falhar a subir, não usar um segredo previsível. Mínimo 256 bits,
HS256, nunca `alg: none`.

## Claims obrigatórios no token

```json
{
  "sub": "uuid-do-usuario",
  "email": "user@email.com",
  "roles": ["ROLE_CUSTOMER"],
  "iat": 1752500000,
  "exp": 1752503600
}
```

## api-gateway — onde cada peça mora (Trilha B, sem hexagonal)

```
api-gateway/src/main/java/br/com/commercehub/apigateway/
├── ApiGatewayApplication.java              ← já existe
└── security/
    ├── JwtAuthenticationFilter.java        ← GlobalFilter: extrai Bearer, valida assinatura/exp
    └── RouteAuthorizationProperties.java   ← mapa rota (+ verbo HTTP) → role exigido
```

Sem `domain/`, `application/` nem `adapter/` — api-gateway é serviço de
infraestrutura (ADR 004), o pacote `security/` é só um pacote técnico
solto, no mesmo nível de `ApiGatewayApplication`.

`JwtAuthenticationFilter` (implementa `GlobalFilter` do Spring Cloud
Gateway):
1. Extrai o header `Authorization: Bearer <token>`.
2. Se a rota é pública (ver tabela abaixo) e não há token, deixa passar.
3. Se há token, valida assinatura (mesmo `JWT_SECRET` do auth-service) e
   expiração — token inválido/expirado → `401`.
4. Verifica se algum role da claim `roles` bate com o role exigido pela
   rota em `RouteAuthorizationProperties` — não bate → `403`.
5. Token válido e autorizado → seta `X-User-Id` (claim `sub`) e
   `X-User-Roles` (claim `roles`) como headers antes de encaminhar a
   requisição ao serviço de destino (ver ADR 005).

`JWT_SECRET` no api-gateway precisa ser **o mesmo valor** usado no
auth-service (HS256 é simétrico) — sem esse alinhamento, todo token
emitido falha a validação no gateway.

## Rotas públicas vs. protegidas

Tabela hoje (só o que já existe no reactor — catalog-service e
auth-service; as demais rotas são adicionadas à tabela conforme cada
serviço for criado):

| Rota | Verbo | Acesso |
|---|---|---|
| `/auth/login` | POST | Pública |
| `/auth/register` | POST | Pública |
| `/api/catalog/products` | GET | Pública |
| `/api/catalog/products/{id}` | GET | Pública |
| `/api/catalog/products` | POST, PUT, DELETE | `ROLE_ADMIN` |
| `/api/catalog/categories` | GET | Pública |
| `/api/catalog/categories` | POST, PUT, DELETE | `ROLE_ADMIN` |

Rotas de `/api/orders/**`, `/api/admin/**` e demais serviços entram nesta
tabela quando esses serviços forem criados — não afirmar aqui um contrato
que ainda não existe no código.

## Propagação de identidade

Não redefine a decisão — ver `ADR 005` para o raciocínio completo. Na
prática, para quem for codar:
- O gateway seta `X-User-Id` e `X-User-Roles` em toda requisição
  autenticada que encaminha.
- Um serviço de negócio só lê esses headers quando uma regra de negócio
  precisa saber "quem" fez a chamada (ex.: order-service confirmando que
  o pedido pertence ao `customerId` do header) — nunca para autenticar
  ou autorizar, isso já foi feito pelo gateway.
- Nenhum serviço de negócio importa lib JWT nem tenta revalidar o token.
  Se isso acontecer, é violação do ADR 005: atualiza o ADR primeiro se a
  decisão precisar mudar, nunca introduz a exceção em silêncio no código.

## docker-compose / application.yml

`JWT_SECRET` é variável de ambiente em **auth-service** e **api-gateway**
(os dois únicos serviços que tocam o segredo), com o mesmo valor nos
dois, quando forem containerizados:

```yaml
auth-service:
  environment:
    JWT_SECRET: ${JWT_SECRET}
    # ... DB_URL/DB_USER/DB_PASS/EUREKA_URL, mesmo padrão de catalog-service

api-gateway:
  environment:
    JWT_SECRET: ${JWT_SECRET}
    EUREKA_URL: http://discovery-service:8761/eureka
```

Sem valor-fallback hardcoded no `docker-compose.yml`/`application.yml`
(diferente de `DB_PASS`, que tem fallback de dev) — `JWT_SECRET` só deve
existir no ambiente real (`.env` local não commitado, ou variável no
Railway).

## Fora de escopo desta skill

Refresh tokens, logout/blacklist de token, OAuth2/social login, rate
limiting e CORS não fazem parte desta skill — não implementar nenhum
desses a menos que explicitamente pedido.
