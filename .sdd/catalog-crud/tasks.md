# Tasks: catalog-service (CRUD de Catálogo)

Quebra de `.sdd/catalog-crud/plan.md` em tasks atômicas. Cada task tem um
critério de aceite testável e independente — implementar fora de ordem
só é seguro respeitando "Depende de".

## Scaffold e infraestrutura

### TASK-01 — Scaffold do módulo catalog-service
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

### TASK-02 — Migration V1: tabela `categories`
**Depende de:** TASK-01
**Descrição:** `db/migration/V1__create_categories.sql` conforme seção 4
do plan.md.
**Critério de aceite:** com Postgres local rodando, subir o serviço
aplica a migration sem erro; `\d catalog.categories` no `psql` mostra
`id, name, created_at, updated_at, version`.

### TASK-03 — Migration V2: tabela `products` (com FK para `categories`)
**Depende de:** TASK-02
**Descrição:** `db/migration/V2__create_products.sql` conforme seção 4
do plan.md, incluindo `CHECK (price >= 0)`.
**Critério de aceite:** migration aplica sem erro; `INSERT` manual via
`psql` com `category_id` inexistente falha por violação de FK; `INSERT`
com `price = -1` falha pelo `CHECK`.

### TASK-04 — Migration V3: tabela `idempotency_keys`
**Depende de:** TASK-01
**Descrição:** `db/migration/V3__create_idempotency_keys.sql` conforme
seção 4 do plan.md — `idempotency_key` como PK.
**Critério de aceite:** migration aplica sem erro; `INSERT` manual de
duas linhas com o mesmo `idempotency_key` falha por violação de
unicidade (testável via `psql`).

## Domain

### TASK-05 — Domain models `Produto` e `Categoria`
**Depende de:** —
**Descrição:** Records em `domain/model/` conforme seção 6 do plan.md.
**Critério de aceite:** módulo compila sem nenhum import de
`jakarta.persistence`; teste unitário instancia os dois records e
verifica os campos (round-trip trivial).

### TASK-06 — Exceções de domínio
**Depende de:** —
**Descrição:** `ProdutoNaoEncontradoException`,
`CategoriaNaoEncontradaException`, `PrecoInvalidoException`,
`CategoriaInexistenteException`, `CategoriaComProdutosAtivosException`
em `domain/exception/` — todas `RuntimeException` simples, sem
dependência de framework.
**Critério de aceite:** teste unitário instancia cada exceção e
verifica a mensagem; nenhuma classe importa Spring.

## Persistência

### TASK-07 — Entidade JPA `ProdutoEntity`
**Depende de:** TASK-03, TASK-05, TASK-06
**Descrição:** `@Entity @Table("products")` em `adapter/persistence/`,
`@ManyToOne` para `CategoriaEntity`, `@Version`, `toDomain()`/
`fromDomain()`.
**Critério de aceite:** teste unitário `fromDomain(produto).toDomain()`
é igual ao `produto` original (round-trip).

### TASK-08 — Entidade JPA `CategoriaEntity`
**Depende de:** TASK-02, TASK-05, TASK-06
**Descrição:** `@Entity @Table("categories")`, `@Version`,
`toDomain()`/`fromDomain()`.
**Critério de aceite:** mesmo teste de round-trip do TASK-07.

### TASK-09 — Entidade JPA `IdempotencyKeyEntity`
**Depende de:** TASK-04
**Descrição:** `@Entity @Table("idempotency_keys")` mapeando todas as
colunas do V3.
**Critério de aceite:** teste `@DataJpaTest` (Testcontainers Postgres —
não H2, precisa de schema e `TIMESTAMPTZ`/`CHECK` reais) grava e lê uma
linha com sucesso.

### TASK-10 — Ports `ProdutoRepository` / `CategoriaRepository`
**Depende de:** TASK-05
**Descrição:** Interfaces em `application/port/`:
`ProdutoRepository` (`findById`, `findAllAtivos(Pageable)`, `save`),
`CategoriaRepository` (`findById`, `findAll(Pageable)`, `save`,
`deleteById`, `existsById`, `countProdutosAtivosPorCategoria(UUID)`).
**Critério de aceite:** interfaces compilam sem import de
`jakarta.persistence` nem de `org.springframework.data` — só tipos de
domínio e `Pageable`/`Page` (utilitário, não framework de persistência).

### TASK-11 — Implementação Spring Data + adapter para `ProdutoRepository`
**Depende de:** TASK-07, TASK-10
**Descrição:** `ProdutoJpaRepository extends JpaRepository<ProdutoEntity, UUID>`
+ classe adapter implementando o port em `adapter/persistence/`.
**Critério de aceite:** teste de integração (Testcontainers Postgres)
salva um `Produto` e recupera pelo id; `findAllAtivos` com produtos
ativos e inativos misturados no banco retorna só os ativos.

### TASK-12 — Implementação Spring Data + adapter para `CategoriaRepository`
**Depende de:** TASK-08, TASK-10
**Descrição:** idem TASK-11 para Categoria, incluindo
`countProdutosAtivosPorCategoria`.
**Critério de aceite:** teste de integração com uma categoria vinculada
a 1 produto ativo e 2 inativos — `countProdutosAtivosPorCategoria`
retorna `1`.

### TASK-13 — Port + implementação `IdempotencyKeyStore` (grava-primeiro)
**Depende de:** TASK-09
**Descrição:** Implementa a estratégia "insert-first" da seção 8 do
plan.md: tenta `INSERT`, captura `DataIntegrityViolationException` em
caso de chave duplicada.
**Critério de aceite:** teste de integração dispara duas gravações
concorrentes com a mesma chave (duas threads/transações) e verifica que
exatamente uma recebe sucesso no `INSERT` e a outra recebe a exceção de
violação de unicidade — reproduz a race condition apontada no plan.md.

## Usecases

### TASK-14 — `CriarProdutoUseCase`
**Depende de:** TASK-06, TASK-11, TASK-13
**Critério de aceite (teste unitário com mocks dos ports):**
(a) preço negativo → `PrecoInvalidoException`;
(b) `categoriaId` inexistente → `CategoriaInexistenteException`;
(c) sem `Idempotency-Key` → sempre chama `save`;
(d) com `Idempotency-Key` já resolvida (mock retorna `resourceId`
preenchido) → retorna o recurso existente sem chamar `save` de novo;
(e) `Idempotency-Key` presente mas com `expires_at < now` → trata como
se não existisse: processa normalmente e cria um novo recurso (TTL de
24h expirando de fato desativa a deduplicação).

### TASK-15 — `AtualizarProdutoUseCase` (PUT, optimistic locking)
**Depende de:** TASK-07, TASK-11, TASK-14
**Descrição:** aplica a nota do plan.md — usar entidade detached com o
`version` do request (não sobrescrever `version` de entidade managed).
**Critério de aceite:** teste de **integração** (não só mock) que
persiste um produto, faz PUT com `version` desatualizado e verifica que
`ObjectOptimisticLockingFailureException` é de fato lançada. Um teste só
com mocks não cobre esse critério — precisa do Hibernate real.

### TASK-16 — `DesativarProdutoUseCase` (DELETE soft)
**Depende de:** TASK-07, TASK-11
**Critério de aceite:**
(a) produto ativo → `ativo=false` após a chamada;
(b) produto já inativo → chamada não lança erro e não altera
`updatedAt` (idempotente);
(c) id inexistente → `ProdutoNaoEncontradoException`.

### TASK-17 — `BuscarProdutoUseCase` e `ListarProdutosUseCase`
**Depende de:** TASK-11
**Critério de aceite:**
(a) GET detail de produto inativo → `ProdutoNaoEncontradoException`;
(b) listagem com produtos ativos e inativos misturados retorna só os
ativos;
(c) listagem sem `sort` explícito vem ordenada por `createdAt DESC`.

### TASK-18 — `CriarCategoriaUseCase`
**Depende de:** TASK-06, TASK-12, TASK-13
**Critério de aceite:** mesmo padrão do TASK-14 (itens c, d e e), sem
validação de preço/categoriaId.

### TASK-19 — `AtualizarCategoriaUseCase` (PUT, optimistic locking)
**Depende de:** TASK-08, TASK-12, TASK-18
**Critério de aceite:** mesmo padrão do TASK-15 aplicado a Categoria
(teste de integração, `version` desatualizado → 409).

### TASK-20 — `DeletarCategoriaUseCase` (DELETE hard, bloqueio por produtos ativos)
**Depende de:** TASK-08, TASK-12
**Critério de aceite:**
(a) categoria sem produtos vinculados → deleta (linha some da tabela,
verificável por `findById` retornando vazio);
(b) categoria com ao menos um produto **ativo** vinculado →
`CategoriaComProdutosAtivosException`, categoria continua no banco;
(c) categoria só com produtos **inativos** vinculados → deleta
normalmente (não bloqueia).

### TASK-21 — `BuscarCategoriaUseCase` e `ListarCategoriasUseCase`
**Depende de:** TASK-12
**Critério de aceite:** GET detail de categoria inexistente →
`CategoriaNaoEncontradaException`; listagem paginada ordenada por
`createdAt DESC`.

## Web

### TASK-22 — DTOs de Produto (request/response) com Bean Validation
**Depende de:** TASK-05
**Descrição:** records em `adapter/web/` com `@NotBlank`/`@DecimalMin`
etc.
**Critério de aceite:** request com `nome` em branco falha o binding
(`MethodArgumentNotValidException`); campo `preco` com tipo inválido no
JSON dispara `HttpMessageNotReadableException`.

### TASK-23 — DTOs de Categoria (request/response)
**Depende de:** TASK-05
**Critério de aceite:** mesmo padrão de validação do TASK-22 para
`nome`.

### TASK-24 — `ProdutoController` (5 endpoints)
**Depende de:** TASK-14 a TASK-17, TASK-22
**Critério de aceite:** teste de integração (`MockMvc`/`WebTestClient`)
cobre um caso de sucesso por endpoint: GET list → 200, GET detail → 200,
POST → 201 com header `Location` contendo a URI do recurso criado (ex.:
`/api/catalog/products/{id}`), PUT → 200, DELETE → 204.

### TASK-25 — `CategoriaController` (5 endpoints)
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
