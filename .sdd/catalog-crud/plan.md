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
  NULL` (`PRODUCT`/`CATEGORY`), `resource_id UUID` (**nullable**),
  `response_status SMALLINT` (**nullable**), `created_at TIMESTAMPTZ NOT
  NULL`, `expires_at TIMESTAMPTZ NOT NULL` (= `created_at` + 24h,
  calculado na aplicação). Índice em `expires_at` para suportar limpeza
  futura (job de limpeza é observação, não requisito desta fase).
  `resource_id`/`response_status` são nullable porque o fluxo
  grava-primeiro da seção 8 faz o `INSERT` da linha *antes* de conhecer
  esses valores — eles só existem depois que o recurso é efetivamente
  criado, preenchidos por um `UPDATE` posterior. `NOT NULL` neles
  tornaria o próprio `INSERT` inicial impossível.

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
| `CriarProdutoUseCase` | valida `preco >= 0` (senão `PrecoInvalidoException`→422); valida `categoriaId` existente (senão `CategoriaInexistenteException`→422); checa `Idempotency-Key` via `IdempotencyKeyStore` antes de processar, com três desfechos: chave nova ou expirada reivindicada → cria e retorna 201; chave já resolvida → devolve o recurso existente com 200; chave em voo (`resource_id` ainda nulo) → `RequisicaoDuplicadaEmAndamentoException`→409 |
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

**Nota sobre o `save` dos adapters de repositório:** os adapters usam
`saveAndFlush`, não `save`. O flush é o que faz o Hibernate emitir o
`UPDATE` ainda dentro da chamada do port, com duas consequências que o
optimistic locking depende: (a) um `version` divergente vira
`ObjectOptimisticLockingFailureException` ali, dentro do usecase, e não lá
no commit da transação — onde ficaria fora do alcance do
`GlobalExceptionHandler` (seção 9) e do teste de integração da task; e (b) o
`version` já incrementado está na entidade devolvida ao chamador, e não um
valor obsoleto. Sem (b), o cliente que fizesse `PUT` e reaproveitasse o
`version` da resposta receberia 409 na requisição seguinte.

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
5. Chave existente e **expirada** (`expires_at <= now`) → o TTL de 24h já
   passou, a deduplicação não vale mais: a requisição deve criar um recurso
   novo. Mas a linha ainda ocupa a PK, então o `INSERT` do passo 1 falha.
   Reivindicar a linha expirada com `tryClaimExpired`: um **`UPDATE`
   condicional** (`WHERE idempotency_key = ? AND expires_at <= now`) que
   renova `created_at`/`expires_at` e **reseta `resource_id` e
   `response_status` para `NULL` na mesma instrução** — a chave volta ao
   mesmo estado "em processamento" que o `INSERT` do passo 2 produz. Se o
   `UPDATE` afetar 1 linha, esta requisição reivindicou a chave e processa a
   criação; se afetar 0 linhas, outra requisição reivindicou primeiro e o
   caso recai no passo 3 (duplicata em voo).

   Não usar `DELETE` seguido de novo `INSERT`: são duas operações, e a
   janela entre elas reabre exatamente a race condition que a estratégia
   grava-primeiro existe para eliminar (dois `POST`s simultâneos sobre uma
   chave expirada deletariam e inseririam cada um, criando dois recursos). O
   `UPDATE` condicional resolve em uma única operação atômica — quem vence é
   decidido pelo próprio banco, sem janela.
6. Sem header → comportamento normal, sempre cria (sem deduplicação).

## 9. Contrato de erros → `GlobalExceptionHandler`
| Exceção capturada | HTTP |
|---|---|
| `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException` (UUID mal formado no path) | 400 |
| `ProdutoNaoEncontradoException`, `CategoriaNaoEncontradaException` | 404 |
| `ObjectOptimisticLockingFailureException` (Spring envolve a exceção do Hibernate) | 409 |
| `RequisicaoDuplicadaEmAndamentoException` (`Idempotency-Key` duplicada, recurso ainda não criado — seção 8, passo 3) | 409 |
| `PrecoInvalidoException`, `CategoriaInexistenteException`, `CategoriaComProdutosAtivosException` | 422 |

Corpo de erro: formato simples e único para todos os casos (`status`,
`message`, `timestamp`) — não definido no `specify.md`, é uma escolha de
implementação, não um requisito do contrato.

## 10. Paginação e ordenação
`GET /products` e `GET /categories` usam `Pageable` do Spring Data e
retornam `Page<T>` (convenção global do CLAUDE.md). Os dois defaults do
`specify.md` moram em camadas diferentes, de propósito:

- **Ordenação padrão (`createdAt DESC`) → nos usecases de listagem.**
  `ListarProdutosUseCase`/`ListarCategoriasUseCase` aplicam esse `Sort`
  quando o `Pageable` recebido chega com o `Sort` vazio; um `sort` explícito
  do chamador é respeitado. É regra de negócio, não detalhe de transporte —
  o `specify.md` a lista na seção "Paginação e ordenação" e de novo nos
  critérios de aceite, e vale para qualquer chamador da listagem, não só o
  HTTP.
- **Tamanho de página padrão (12 produtos / 20 categorias) → no
  `@PageableDefault` dos controllers.** Esse *é* detalhe de transporte: é o
  que a API oferece a um cliente que não pediu `size`.

Consequência prática: não mova o `sort` de volta para o `@PageableDefault`.
Lá ele não seria verificável pelos testes de usecase (o critério (c) da
TASK-17), e a regra vazaria para o adapter web.

## 11. Fora do escopo (herdado do specify.md)
Sem eventos (`ProductUpdated`), sem PATCH, sem hierarquia de categoria,
sem enforcement real de autenticação/role "admin", sem job de limpeza de
`idempotency_keys` expiradas.
