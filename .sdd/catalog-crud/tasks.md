# Tasks: catalog-service (CRUD de Catálogo)

Quebra de `.sdd/catalog-crud/plan.md` em tasks atômicas. Cada task tem um
critério de aceite testável e independente — implementar fora de ordem
só é seguro respeitando "Depende de".

`[x]` no título = task commitada (todo commit referencia o `TASK-N`
correspondente; `git log --oneline | grep TASK-N` confirma). Marcar ao
concluir cada task, não em lote no fim.

## Scaffold e infraestrutura

### [x] TASK-01 — Scaffold do módulo catalog-service
**Depende de:** —
**Descrição:** Criar `catalog-service/pom.xml` (Trilha A do
`microservice-scaffold` + `spring-boot-starter-validation`), adicionar
`<module>catalog-service</module>` no `pom.xml` raiz, `application.yml`
(seção 2 do plan.md), `Dockerfile`, `CLAUDE.md` local, classe
`CatalogServiceApplication`.
**Critério de aceite:** `mvn install -pl catalog-service -am` compila com
sucesso; ao subir o serviço (com `discovery-service` rodando), o log
mostra o registro no Eureka e `http://localhost:8761` lista
`CATALOG-SERVICE`.

### [x] TASK-02 — Migration V1: tabela `categories`
**Depende de:** TASK-01
**Descrição:** `db/migration/V1__create_categories.sql` conforme seção 4
do plan.md.
**Critério de aceite:** com Postgres local rodando, subir o serviço
aplica a migration sem erro; `\d catalog.categories` no `psql` mostra
`id, name, created_at, updated_at, version`.

### [x] TASK-03 — Migration V2: tabela `products` (com FK para `categories`)
**Depende de:** TASK-02
**Descrição:** `db/migration/V2__create_products.sql` conforme seção 4
do plan.md, incluindo `CHECK (price >= 0)`.
**Critério de aceite:** migration aplica sem erro; `INSERT` manual via
`psql` com `category_id` inexistente falha por violação de FK; `INSERT`
com `price = -1` falha pelo `CHECK`.

### [x] TASK-04 — Migration V3: tabela `idempotency_keys`
**Depende de:** TASK-01
**Descrição:** `db/migration/V3__create_idempotency_keys.sql` conforme
seção 4 do plan.md — `idempotency_key` como PK.
**Critério de aceite:** migration aplica sem erro; `INSERT` manual de
duas linhas com o mesmo `idempotency_key` falha por violação de
unicidade (testável via `psql`).

## Domain

### [x] TASK-05 — Domain models `Product` e `Category`
**Depende de:** —
**Descrição:** Records em `domain/model/` conforme seção 6 do plan.md.
**Critério de aceite:** módulo compila sem nenhum import de
`jakarta.persistence`; teste unitário instancia os dois records e
verifica os campos (round-trip trivial).

### [x] TASK-06 — Exceções de domínio
**Depende de:** —
**Descrição:** `ProductNotFoundException`,
`CategoryNotFoundException`, `InvalidPriceException`,
`InvalidCategoryException`, `CategoryHasActiveProductsException`,
`DuplicateRequestInProgressException` (`Idempotency-Key` duplicada
cujo recurso ainda não foi criado — seção 8 do plan.md, passo 3)
em `domain/exception/` — todas `RuntimeException` simples, sem
dependência de framework.
**Critério de aceite:** teste unitário instancia cada exceção e
verifica a mensagem; nenhuma classe importa Spring.

## Persistência

### [x] TASK-07 — Entidade JPA `ProductEntity`
**Depende de:** TASK-03, TASK-05, TASK-06
**Descrição:** `@Entity @Table("products")` em `adapter/persistence/`,
`@ManyToOne` para `CategoryEntity`, `@Version`, `toDomain()`/
`fromDomain()`.
**Critério de aceite:** teste unitário `fromDomain(produto).toDomain()`
é igual ao `produto` original (round-trip).

### [x] TASK-08 — Entidade JPA `CategoryEntity`
**Depende de:** TASK-02, TASK-05, TASK-06
**Descrição:** `@Entity @Table("categories")`, `@Version`,
`toDomain()`/`fromDomain()`.
**Critério de aceite:** mesmo teste de round-trip do TASK-07.

### [x] TASK-09 — Entidade JPA `IdempotencyKeyEntity`
**Depende de:** TASK-04
**Descrição:** `@Entity @Table("idempotency_keys")` mapeando todas as
colunas do V3.
**Critério de aceite:** teste `@DataJpaTest` (Testcontainers Postgres —
não H2, precisa de schema e `TIMESTAMPTZ`/`CHECK` reais) grava e lê uma
linha com sucesso.

### [x] TASK-10 — Ports `ProductRepository` / `CategoryRepository`
**Depende de:** TASK-05
**Descrição:** Interfaces em `application/port/`:
`ProductRepository` (`findById`, `findAllActive(Pageable)`, `save`),
`CategoryRepository` (`findById`, `findAll(Pageable)`, `save`,
`deleteById`, `existsById`, `countActiveProductsByCategory(UUID)`).
**Critério de aceite:** interfaces compilam sem import de
`jakarta.persistence` nem de `org.springframework.data` — só tipos de
domínio e `Pageable`/`Page` (utilitário, não framework de persistência).

### [x] TASK-11 — Implementação Spring Data + adapter para `ProductRepository`
**Depende de:** TASK-07, TASK-10
**Descrição:** `ProductJpaRepository extends JpaRepository<ProductEntity, UUID>`
+ classe adapter implementando o port em `adapter/persistence/`.
**Critério de aceite:** teste de integração (Testcontainers Postgres)
salva um `Product` e recupera pelo id; `findAllActive` com produtos
ativos e inativos misturados no banco retorna só os ativos.

### [x] TASK-12 — Implementação Spring Data + adapter para `CategoryRepository`
**Depende de:** TASK-08, TASK-10
**Descrição:** idem TASK-11 para Categoria, incluindo
`countActiveProductsByCategory`.
**Critério de aceite:** teste de integração com uma categoria vinculada
a 1 produto ativo e 2 inativos — `countActiveProductsByCategory`
retorna `1`.

### [x] TASK-13 — Port + implementação `IdempotencyKeyStore` (grava-primeiro)
**Depende de:** TASK-09
**Descrição:** Implementa a estratégia "insert-first" da seção 8 do
plan.md: tenta `INSERT`, captura `DataIntegrityViolationException` em
caso de chave duplicada. Inclui `tryClaimExpired` — o `UPDATE`
condicional (`WHERE idempotency_key = ? AND expires_at <= now`) do passo
5 da seção 8, que renova `created_at`/`expires_at` e reseta
`resource_id`/`response_status` para `NULL` na mesma instrução.
**Critério de aceite:**
(a) teste de integração dispara duas gravações
concorrentes com a mesma chave (duas threads/transações) e verifica que
exatamente uma recebe sucesso no `INSERT` e a outra recebe a exceção de
violação de unicidade — reproduz a race condition apontada no plan.md;
(b) duas threads chamando `tryClaimExpired` sobre a mesma chave expirada
— exatamente uma recebe `true` (a atomicidade do `UPDATE` condicional é
o que garante isso; `DELETE` + `INSERT` falharia aqui);
(c) após uma reivindicação bem-sucedida, `resource_id` e
`response_status` da linha voltaram a `NULL`.

## Usecases

### [x] TASK-14 — `CreateProductUseCase`
**Depende de:** TASK-06, TASK-11, TASK-13
**Critério de aceite (teste unitário com mocks dos ports):**
(a) preço negativo → `InvalidPriceException`;
(b) `categoryId` inexistente → `InvalidCategoryException`;
(c) sem `Idempotency-Key` → sempre chama `save`;
(d) com `Idempotency-Key` já resolvida (mock retorna `resourceId`
preenchido) → retorna o recurso existente sem chamar `save` de novo;
(e) `Idempotency-Key` presente mas com `expires_at < now` → trata como
se não existisse: reivindica a chave via `tryClaimExpired`, processa
normalmente e cria um novo recurso (TTL de 24h expirando de fato
desativa a deduplicação). Se `tryClaimExpired` retornar `false` (outra
requisição reivindicou primeiro) → `DuplicateRequestInProgressException`;
(f) `Idempotency-Key` presente, **não** expirada e com `resource_id`
ainda nulo (outra requisição em voo, seção 8 passo 3) →
`DuplicateRequestInProgressException`, **sem** chamar `save` — é o
que o `GlobalExceptionHandler` (TASK-26) mapeia para 409.

### [x] TASK-15 — `UpdateProductUseCase` (PUT, optimistic locking)
**Depende de:** TASK-07, TASK-11, TASK-14
**Descrição:** aplica a nota do plan.md — usar entidade detached com o
`version` do request (não sobrescrever `version` de entidade managed).
**Critério de aceite:** teste de **integração** (não só mock) que
persiste um produto, faz PUT com `version` desatualizado e verifica que
`ObjectOptimisticLockingFailureException` é de fato lançada. Um teste só
com mocks não cobre esse critério — precisa do Hibernate real.

### [x] TASK-16 — `DeactivateProductUseCase` (DELETE soft)
**Depende de:** TASK-07, TASK-11
**Critério de aceite:**
(a) produto ativo → `active=false` após a chamada;
(b) produto já inativo → chamada não lança erro e não altera
`updatedAt` (idempotente);
(c) id inexistente → `ProductNotFoundException`.

### [x] TASK-17 — `GetProductUseCase` e `ListProductsUseCase`
**Depende de:** TASK-11
**Critério de aceite:**
(a) GET detail de produto inativo → `ProductNotFoundException`;
(b) listagem com produtos ativos e inativos misturados retorna só os
ativos;
(c) listagem sem `sort` explícito vem ordenada por `createdAt DESC`.

### [x] TASK-18 — `CreateCategoryUseCase`
**Depende de:** TASK-06, TASK-12, TASK-13
**Descrição:** segunda cópia do fluxo grava-primeiro — extraí-lo para o
colaborador `IdempotentCreation` (seção 8 do plan.md) em vez de duplicar a
lógica de corrida, e reescrever `CreateProductUseCase` sobre ele.
**Critério de aceite:** mesmo padrão do TASK-14 (itens c, d, e e f), sem
validação de preço/`categoryId`. Os testes do TASK-14 continuam passando
sobre o fluxo extraído — é o que garante que a extração não mudou
comportamento.

### [x] TASK-19 — `UpdateCategoryUseCase` (PUT, optimistic locking)
**Depende de:** TASK-08, TASK-12, TASK-18
**Critério de aceite:** mesmo padrão do TASK-15 aplicado a Categoria
(teste de integração, `version` desatualizado → 409).

### [x] TASK-20 — `DeleteCategoryUseCase` (DELETE hard, bloqueio por produtos ativos)
**Depende de:** TASK-08, TASK-12
**Critério de aceite:**
(a) categoria sem produtos vinculados → deleta (linha some da tabela,
verificável por `findById` retornando vazio);
(b) categoria com ao menos um produto **ativo** vinculado →
`CategoryHasActiveProductsException`, categoria continua no banco;
(c) categoria só com produtos **inativos** vinculados → deleta
normalmente (não bloqueia).

### [x] TASK-21 — `GetCategoryUseCase` e `ListCategoriesUseCase`
**Depende de:** TASK-12
**Critério de aceite:** GET detail de categoria inexistente →
`CategoryNotFoundException`; listagem paginada ordenada por
`createdAt DESC`.

## Web

### [x] TASK-22 — DTOs de Produto (request/response) com Bean Validation
**Depende de:** TASK-05
**Descrição:** records em `adapter/web/` com `@NotBlank`/`@DecimalMin`
etc.
**Critério de aceite:** request com `name` em branco falha o binding
(`MethodArgumentNotValidException`); campo `price` com tipo inválido no
JSON dispara `HttpMessageNotReadableException`.

### [x] TASK-23 — DTOs de Categoria (request/response)
**Depende de:** TASK-05
**Critério de aceite:** mesmo padrão de validação do TASK-22 para
`name`.

### [x] TASK-24 — `ProductController` (5 endpoints)
**Depende de:** TASK-14 a TASK-17, TASK-22
**Critério de aceite:** teste de integração (`MockMvc`/`WebTestClient`)
cobre um caso de sucesso por endpoint: GET list → 200, GET detail → 200,
POST → 201 com header `Location` contendo a URI do recurso criado (ex.:
`/api/catalog/products/{id}`), PUT → 200, DELETE → 204.

### [x] TASK-25 — `CategoryController` (5 endpoints)
**Depende de:** TASK-18 a TASK-21, TASK-23
**Critério de aceite:** mesmo padrão do TASK-24 (incluindo POST → 201
com header `Location`), mais DELETE → 422 quando há produto ativo
vinculado (teste ponta a ponta via HTTP, não só usecase isolado).

### TASK-26 — `GlobalExceptionHandler`
**Depende de:** TASK-06, TASK-24, TASK-25
**Descrição:** `@RestControllerAdvice` mapeando a tabela da seção 9 do
plan.md.
**Critério de aceite:** teste de integração dispara, via chamada HTTP
real, cada cenário da tabela (400/404/409/422) e verifica que o status
retornado bate exatamente com o esperado — um teste por status, não um
teste genérico.

## Fim-a-fim

### TASK-27 — Fluxo completo de idempotência via HTTP
**Depende de:** TASK-24, TASK-25
**Critério de aceite:** dois `POST` HTTP sequenciais com o mesmo
`Idempotency-Key` retornam 201 na primeira e 200 na segunda, com o
mesmo id de recurso no corpo; disparados **simultaneamente** (mesma
key), resultam em exatamente um recurso criado no banco.

### TASK-28 — Fluxo completo de optimistic locking via HTTP
**Depende de:** TASK-24, TASK-25
**Critério de aceite:** `PUT` com `version` correto retorna 200; um
segundo `PUT` reaproveitando o `version` antigo (sem refazer o GET)
retorna 409.
