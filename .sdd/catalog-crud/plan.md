# Plan: catalog-service (CRUD de Catálogo)

Tradução técnica de `.sdd/catalog-crud/specify.md` para arquitetura
concreta. Segue `microservice-scaffold` (Trilha A) para o esqueleto e
`jpa-schema` para persistência/schema. Não cobre tasks nem código —
apenas o desenho.

## 1. Scaffold do serviço
- Nome: `catalog-service`, pacote base `br.com.commercehub.catalog`,
  porta `8082` (tabela de portas do `microservice-scaffold`).
- Adicionar `<module>catalog-service</module>` no `pom.xml` raiz.
- `pom.xml` filho: dependências padrão da Trilha A (`spring-boot-
  starter-web`, `spring-boot-starter-data-jpa`, `spring-cloud-starter-
  netflix-eureka-client`, `postgresql`, `flyway-core`, `flyway-database-
  postgresql`) **mais** `spring-boot-starter-validation` (Bean Validation
  para os DTOs de request — `@NotBlank`, `@DecimalMin`, etc.; não faz
  parte da lista padrão do scaffold, mas é necessária aqui).
- Sem `spring-cloud-starter-openfeign` e sem `spring-kafka`: catalog-
  service não chama nenhum outro serviço de forma síncrona (é sempre o
  lado chamado, ex.: `qualquer-serviço → catalog-service`, ADR 003) e não
  publica/consome eventos nesta fase (`ProductUpdated` está fora do
  escopo — ver specify.md).

## 2. `application.yml` (schema isolation — skill jpa-schema)
```yaml
spring:
  application:
    name: catalog-service
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/commercedb}
    username: ${DB_USER:commerce}
    password: ${DB_PASS:commerce123}
  jpa:
    properties:
      hibernate:
        default_schema: catalog
    hibernate:
      ddl-auto: validate
  flyway:
    schemas: catalog
    locations: classpath:db/migration

server:
  port: 8082

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka}
```

## 3. Estrutura de pacotes (Clean Architecture — CLAUDE.md raiz)
```
catalog-service/src/main/java/br/com/commercehub/catalog/
├── domain/
│   ├── model/       Produto, Categoria (records)
│   └── exception/   ProdutoNaoEncontradoException, CategoriaNaoEncontradaException,
│                     PrecoInvalidoException, CategoriaInexistenteException,
│                     CategoriaComProdutosAtivosException
├── application/
│   ├── port/        ProdutoRepository, CategoriaRepository, IdempotencyKeyStore
│   └── usecase/      um usecase por operação (ver seção 7)
└── adapter/
    ├── persistence/  ProdutoEntity, CategoriaEntity, IdempotencyKeyEntity,
    │                 repositórios Spring Data + implementação dos ports
    └── web/          ProdutoController, CategoriaController, DTOs (records),
                       GlobalExceptionHandler (@RestControllerAdvice)
```
Sem `adapter/messaging/` nesta fase (sem eventos — fora do escopo).

## 4. Migrations Flyway (`src/main/resources/db/migration/`)
```
V1__create_categories.sql
V2__create_products.sql
V3__create_idempotency_keys.sql
```

- `categories`: `id UUID PK`, `name VARCHAR(255) NOT NULL`,
  `created_at TIMESTAMPTZ NOT NULL`, `updated_at TIMESTAMPTZ NOT NULL`,
  `version BIGINT NOT NULL DEFAULT 0`.
- `products`: `id UUID PK`, `name VARCHAR(255) NOT NULL`,
  `description TEXT`, `price NUMERIC(10,2) NOT NULL CHECK (price >= 0)`,
  `category_id UUID NOT NULL REFERENCES categories(id)`,
  `active BOOLEAN NOT NULL DEFAULT true`, `created_at TIMESTAMPTZ NOT
  NULL`, `updated_at TIMESTAMPTZ NOT NULL`, `version BIGINT NOT NULL
  DEFAULT 0`.
- `idempotency_keys`: `idempotency_key UUID PK` (o valor do header),
  `http_method VARCHAR(10) NOT NULL`, `resource_type VARCHAR(50) NOT
  NULL` (`PRODUCT`/`CATEGORY`), `resource_id UUID NOT NULL`,
  `response_status SMALLINT NOT NULL`, `created_at TIMESTAMPTZ NOT
  NULL`, `expires_at TIMESTAMPTZ NOT NULL` (= `created_at` + 24h,
  calculado na aplicação). Índice em `expires_at` para suportar limpeza
  futura (job de limpeza é observação, não requisito desta fase).

Todos os tipos batem com as convenções globais: `UUID`, `NUMERIC(10,2)`
⇄ `BigDecimal`, `TIMESTAMPTZ` ⇄ `OffsetDateTime`.

## 5. Entidades JPA (`adapter/persistence/`)
- `ProdutoEntity` (`@Table("products")`): campos batem com `V2`;
  `@ManyToOne(fetch = LAZY) @JoinColumn(name = "category_id")` para
  `CategoriaEntity` — permitido porque é o mesmo schema/serviço (ADR
  004); `@Version private long version` (Hibernate gerencia o
  incremento e a checagem de conflito automaticamente).
- `CategoriaEntity` (`@Table("categories")`): mesmo padrão, com
  `@Version`.
- `IdempotencyKeyEntity` (`@Table("idempotency_keys")`).
- Todas com `toDomain()`/`fromDomain()` — nunca expostas na API (padrão
  `jpa-schema`).

## 6. Domain models (`domain/model/` — sem JPA)
```java
public record Produto(
    UUID id, String nome, String descricao, BigDecimal preco,
    UUID categoriaId, boolean ativo,
    OffsetDateTime createdAt, OffsetDateTime updatedAt, long version
) {}

public record Categoria(
    UUID id, String nome,
    OffsetDateTime createdAt, OffsetDateTime updatedAt, long version
) {}
```

## 7. Usecases (`application/usecase/`) — regra de negócio por operação
| Usecase | Regra |
|---|---|
| `CriarProdutoUseCase` | valida `preco >= 0` (senão `PrecoInvalidoException`→422); valida `categoriaId` existente (senão `CategoriaInexistenteException`→422); checa `Idempotency-Key` via `IdempotencyKeyStore` antes de processar |
| `AtualizarProdutoUseCase` (PUT) | mesmas validações de criação; o `version` recebido no request precisa efetivamente participar da checagem de conflito (ver nota abaixo) — Hibernate compara com o valor atual da coluna no `UPDATE` e lança `OptimisticLockException` se divergir |
| `DesativarProdutoUseCase` (DELETE) | busca por id (404 se não existir); se já `ativo=false`, no-op idempotente (204); senão seta `ativo=false` e `updatedAt=now` |
| `BuscarProdutoUseCase` (GET detail) | 404 se não existir OU `ativo=false` |
| `ListarProdutosUseCase` (GET list) | `Pageable`, filtra `ativo=true`, sort padrão `createdAt DESC` |
| `CriarCategoriaUseCase` | mesma checagem de `Idempotency-Key` que produto |
| `AtualizarCategoriaUseCase` (PUT) | mesmo padrão de optimistic locking (ver nota abaixo) |
| `DeletarCategoriaUseCase` (DELETE) | 404 se não existir; conta produtos **ativos** com aquele `categoriaId` — se `> 0`, `CategoriaComProdutosAtivosException`→422; senão delete físico (204) |
| `BuscarCategoriaUseCase` / `ListarCategoriasUseCase` | GET detail (404) / GET list paginado, sort `createdAt DESC` |

**Nota para a task de PUT/optimistic locking:** carregar a entidade
gerenciada (managed) e sobrescrever seu `version` manualmente com o valor
do request pode ser silenciosamente ignorado pelo Hibernate — a entidade
managed usa o `version` que ela mesma carregou do banco, não o que foi
atribuído depois. A implementação correta é uma destas duas: (a) montar
uma entidade **detached** com o `id` + `version` do request e usar
`merge`/`save`, deixando o Hibernate comparar esse `version` contra o
valor atual da coluna no `UPDATE`; ou (b) comparar explicitamente
`request.version() != entidadeCarregada.getVersion()` e lançar o 409 na
mão antes de aplicar as mudanças. Isso não precisa ser resolvido aqui,
mas a task correspondente deve deixar isso explícito — um teste que só
verifica "PUT com version errado dá 409" pode passar por acidente mesmo
com a implementação ingênua (managed) se o teste não isolar a
transação/sessão corretamente.

## 8. Idempotência (POST)
`IdempotencyKeyStore` (port) + implementação em `adapter/persistence`
sobre `idempotency_keys`. Fluxo em `CriarProdutoUseCase`/
`CriarCategoriaUseCase` — **grava-primeiro**, não "busca depois grava":
1. Se o header `Idempotency-Key` vier presente, tenta **inserir** a linha
   em `idempotency_keys` (a chave é a PK) antes de criar o recurso.
2. `INSERT` bem-sucedido → esta requisição "ganhou a corrida": processa
   a criação normalmente, faz `UPDATE` da linha com `resource_id` +
   `response_status=201`, retorna **201**.
3. `INSERT` falha por violação de unicidade (`DataIntegrityViolationException`)
   → outra requisição concorrente já está processando/processou essa
   chave. Busca a linha existente: se já tiver `resource_id` preenchido,
   retorna o recurso com **200**; se ainda não (a outra requisição está
   em voo), retorna **409** para o cliente tentar de novo — evita a
   janela "busca → não achou → processa" onde dois `POST`s simultâneos
   com a mesma key criariam dois recursos.
4. Chave existente e não expirada (`expires_at > now`), fora de uma
   corrida (fluxo normal de retry) → passo 3 cobre também esse caso.
5. Sem header → comportamento normal, sempre cria (sem deduplicação).

## 9. Contrato de erros → `GlobalExceptionHandler`
| Exceção capturada | HTTP |
|---|---|
| `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException` (UUID mal formado no path) | 400 |
| `ProdutoNaoEncontradoException`, `CategoriaNaoEncontradaException` | 404 |
| `ObjectOptimisticLockingFailureException` (Spring envolve a exceção do Hibernate) | 409 |
| `PrecoInvalidoException`, `CategoriaInexistenteException`, `CategoriaComProdutosAtivosException` | 422 |

Corpo de erro: formato simples e único para todos os casos (`status`,
`message`, `timestamp`) — não definido no `specify.md`, é uma escolha de
implementação, não um requisito do contrato.

## 10. Paginação
`GET /products` e `GET /categories` usam `Pageable` do Spring Data com
`@PageableDefault(sort = "createdAt", direction = DESC, size = 12 | 20)`
conforme o default de cada endpoint no `specify.md`. Retornam `Page<T>`
(convenção global do CLAUDE.md).

## 11. Fora do escopo (herdado do specify.md)
Sem eventos (`ProductUpdated`), sem PATCH, sem hierarquia de categoria,
sem enforcement real de autenticação/role "admin", sem job de limpeza de
`idempotency_keys` expiradas.
