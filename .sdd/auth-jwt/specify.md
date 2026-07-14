# Spec: Registro + Login com JWT (auth-service)

## Responsabilidade do auth-service
Cadastro público (self-service) e autenticação de usuários. Emite o JWT
que o api-gateway valida (ADR 005). Não gerencia perfil, endereço ou
qualquer dado de negócio — isso é customer-service e os demais serviços
(ADR 004: auth-service conhece só "Usuário (credenciais, papel)").

## Modelo de dados

### User
| Campo | Tipo | Regra |
|---|---|---|
| id | UUID | gerado no create |
| email | String | obrigatório, `VARCHAR(255)`, normalizado para lowercase antes de gravar/comparar, único (constraint `UNIQUE` no banco — não só checagem na usecase) |
| passwordHash | String | BCrypt strength 12; nunca exposto em nenhuma response |
| roles | `Set<Role>` | sempre `[ROLE_CUSTOMER]` no cadastro público; nunca vazio |
| createdAt | OffsetDateTime | imutável |

`Role` — enum fechado: `ROLE_CUSTOMER`, `ROLE_ADMIN`. Nenhum outro valor.

Sem `updatedAt`/`version`: não há endpoint de update de usuário nesta
spec (ver Fora do escopo), então não há optimistic locking a proteger.

## Endpoints

```
POST /auth/register   (público)
POST /auth/login      (público)
GET  /auth/me          (autenticado — qualquer role)
```

`RegisterRequest` **não tem campo `role`/`roles`** — o cliente não pode
influenciar o papel atribuído. A usecase sempre grava `roles =
[ROLE_CUSTOMER]`, independente do que vier no payload (o campo nem existe
no DTO, então não há o que ignorar ou rejeitar). O primeiro `ROLE_ADMIN`
não é criado por nenhum endpoint desta spec — entra via seed/migration
SQL manual (ver Fora do escopo).

`GET /auth/me` não é validado pelo próprio auth-service. Por ADR 005, o
api-gateway é o único ponto que verifica assinatura/expiração do JWT;
mesmo sendo o emissor, auth-service trata essa rota exatamente como
qualquer serviço de negócio trataria uma rota autenticada: lê o usuário a
partir do header `X-User-Id` que o gateway já propagou, sem reabrir o
token. auth-service não ganha `spring-boot-starter-security` nem um
`SecurityFilterChain` por causa desta rota.

## Contrato de erros (HTTP status)
| Status | Quando |
|---|---|
| 200 | `POST /auth/login` com sucesso; `GET /auth/me` com sucesso |
| 201 | `POST /auth/register` cria novo usuário (sem `Location` — ver Response bodies) |
| 400 | payload malformado; email com formato inválido; senha fora de 8–72 caracteres |
| 401 | `POST /auth/login` com email inexistente **ou** senha incorreta — mesma mensagem genérica ("credenciais inválidas") nos dois casos, para não vazar quais emails estão cadastrados |
| 409 | `POST /auth/register` com email já cadastrado |

## Regras de negócio (usecase)
- Email: trim + lowercase antes de comparar ou persistir — `Joao@X.com` e
  `joao@x.com` são o mesmo usuário.
- Unicidade de email é garantida por constraint `UNIQUE` na migration
  Flyway (schema `auth`, ver ADR 002) — a checagem na usecase existe só
  para devolver o 409 amigável antes de tentar o insert; sob duas
  requisições concorrentes com o mesmo email, é a constraint do banco que
  decide, igual ao papel do `version` no catalog-service para escrita
  concorrente.
- Senha: 8 a 72 **caracteres**, validado via Bean Validation
  (`@Size(min = 8, max = 72)`) no `RegisterRequest`. O limite de 72 é uma
  referência ao limite real do BCrypt, que é em **bytes**, não
  caracteres — a validação conta caracteres por simplicidade, então não
  é exatamente a mesma coisa (ver Fora do escopo: caractere multi-byte
  pode passar na validação e ainda assim ser truncado pelo BCrypt).
- `RegisterRequest` sempre resulta em `roles = [ROLE_CUSTOMER]` — nunca
  aceita o cliente influenciar o papel atribuído (ver Endpoints).
- Login com credenciais inválidas (email não encontrado OU senha não
  bate o hash) lança sempre a mesma `InvalidCredentialsException` → 401,
  sem diferenciar os dois casos na mensagem ou no comportamento.
- Sem conceito de conta desabilitada/bloqueada nesta fase — todo usuário
  cadastrado pode logar enquanto existir.
- Sem logout: o token é stateless; o cliente descarta o token para
  "deslogar". Não há endpoint de logout nem blacklist nesta fase.
- Sem refresh token: token expira em 1h (3600s) fixo a partir do `iat`;
  expirado, o cliente precisa logar de novo em `/auth/login`.

## Token (JWT)
Claims conforme a skill `spring-security-jwt`:
```json
{
  "sub": "uuid-do-usuario",
  "email": "user@email.com",
  "roles": ["ROLE_CUSTOMER"],
  "iat": ...,
  "exp": ...
}
```
Algoritmo HS256, segredo via `JWT_SECRET` sem fallback (ADR 005 e skill
`spring-security-jwt` já cobrem isso — não redefinido aqui). Expiração:
3600 segundos, valor configurável mas com esse default.

## Response bodies

`POST /auth/register` → 201, corpo é o usuário criado (sem
`passwordHash`): `id`, `email`, `roles`, `createdAt`. **Sem header
`Location`** — diferente do padrão de 201 estabelecido no catalog-service
(`Location: /api/catalog/products/{id}`), porque não existe (e não entra
nesta spec) um `GET /auth/users/{id}`/`GET /auth/users/{email}` para o
header apontar. Criar esse endpoint só para sustentar o `Location` seria
escopo extra não pedido; decisão: 201 sem `Location` nesta fase.

`POST /auth/login` → 200, corpo inclui token + dados do usuário, para o
cliente montar UI sem uma segunda chamada:
```json
{
  "token": "...",
  "expiresIn": 3600,
  "id": "uuid",
  "email": "user@email.com",
  "roles": ["ROLE_CUSTOMER"]
}
```

`GET /auth/me` → 200, mesmo formato de usuário do register (`id`,
`email`, `roles`, `createdAt`), sem token (o cliente já tem o token que
usou para chamar a rota).

## Dependências (auth-service)
`spring-security-crypto` (só `BCryptPasswordEncoder`) + `jjwt-api`/
`jjwt-impl`/`jjwt-jackson` — **não** `spring-boot-starter-security`
completo. O starter completo tranca todas as rotas por padrão
(autoconfiguração gera login form/senha aleatória), o que exigiria um
`SecurityFilterChain` só para reabrir `/auth/register` e `/auth/login`
como públicas. Com a dependência mínima, essas rotas já nascem públicas
— não há filtro de segurança nenhum rodando dentro de auth-service.

## Critérios de aceite
- [ ] `User` tem: id, email (único, lowercase, `VARCHAR(255)`),
      passwordHash (BCrypt strength 12, nunca exposto), roles
      (`Set<Role>`), createdAt.
- [ ] `Role` só aceita `ROLE_CUSTOMER`/`ROLE_ADMIN`.
- [ ] `POST /auth/register`: cria usuário com `roles=[ROLE_CUSTOMER]`
      sempre, independente do payload; retorna 201 sem `Location`, com
      o usuário (sem hash); email duplicado (case-insensitive) retorna
      409; senha fora de 8–72 caracteres retorna 400.
- [ ] Unicidade de email é `UNIQUE` no banco, não só checagem na
      usecase.
- [ ] `POST /auth/login`: sucesso retorna 200 com token + dados do
      usuário; email inexistente ou senha errada retornam 401 com
      mensagem genérica idêntica.
- [ ] Token JWT contém `sub`, `email`, `roles`, `iat`, `exp`; HS256;
      expira em 3600s; segredo via `JWT_SECRET` sem default.
- [ ] `GET /auth/me`: retorna dados do usuário lendo `X-User-Id`
      propagado pelo gateway — auth-service não valida o token nesta
      rota.
- [ ] auth-service não declara `spring-boot-starter-security` nem tem
      `SecurityFilterChain` próprio.
- [ ] Nenhum endpoint cria usuário com `ROLE_ADMIN`.

## Fora do escopo
- Refresh token, logout, blacklist de token.
- Conta desabilitada/bloqueada.
- Endpoint para promover usuário a admin — primeiro `ROLE_ADMIN` via
  seed/migration SQL manual, fora desta spec.
- Alteração de senha / recuperação de senha ("esqueci minha senha").
- Atualização de perfil de usuário (nome, endereço — isso é
  customer-service, ver ADR 004).
- Rate limiting / proteção contra força bruta no login.
- `spring-boot-starter-security` completo / `SecurityFilterChain` em
  auth-service (ver Dependências).
- Validação de JWT pelo próprio auth-service — quem valida é sempre o
  api-gateway (ADR 005).
- Precisão byte-a-byte na validação de senha: `@Size(min=8, max=72)`
  conta **caracteres**, não bytes. Uma senha com caractere multi-byte
  (acento, emoji) pode ter ≤ 72 caracteres e ainda assim ultrapassar 72
  bytes em UTF-8, sendo truncada silenciosamente pelo BCrypt antes da
  validação perceber. Edge case conhecido e não tratado nesta fase —
  aceitável para projeto de aprendizado; corrigir exigiria validar o
  `byte[]` da senha (`getBytes(UTF_8).length`) em vez do `String.length()`.
- Header `Location` no 201 de `POST /auth/register` — decisão explícita
  de **não** incluir (ver Response bodies), diferente do padrão do
  catalog-service, porque não há `GET /auth/users/{id}` nesta spec para
  o header apontar.
