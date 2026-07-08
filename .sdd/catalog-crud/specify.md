# Spec: CRUD de Catálogo

## Responsabilidade do catalog-service
Gerenciar produtos e categorias. Fornecer consultas paginadas.
Não gerencia estoque (isso é inventory-service).

## Modelo de dados

### Produto
| Campo | Tipo | Regra |
|---|---|---|
| id | UUID | gerado no create |
| nome | String | obrigatório, não vazio |
| descricao | String | opcional |
| preco | BigDecimal | `>= 0`, nunca negativo (zero permitido) |
| categoriaId | UUID | deve referenciar uma Categoria existente |
| ativo | boolean | default `true`; `false` = soft-deleted |
| createdAt | OffsetDateTime | imutável |
| updatedAt | OffsetDateTime | atualizado a cada PUT |
| version | long | optimistic locking (`@Version`) |

### Categoria
| Campo | Tipo | Regra |
|---|---|---|
| id | UUID | gerado no create |
| nome | String | obrigatório, não vazio |
| createdAt | OffsetDateTime | imutável |
| updatedAt | OffsetDateTime | atualizado a cada PUT |
| version | long | optimistic locking (`@Version`) |

Categoria é plana — sem campo de categoria-pai/subcategoria (ver Fora do
escopo).

## Endpoints

### Produtos
```
GET    /api/catalog/products?page=0&size=12&sort=createdAt,desc
GET    /api/catalog/products/{id}
POST   /api/catalog/products                 (admin, Idempotency-Key opcional)
PUT    /api/catalog/products/{id}             (admin, update completo, version obrigatório)
DELETE /api/catalog/products/{id}             (admin, soft delete, idempotente)
```

### Categorias
```
GET    /api/catalog/categories?page=0&size=20&sort=createdAt,desc
GET    /api/catalog/categories/{id}
POST   /api/catalog/categories                (admin, Idempotency-Key opcional)
PUT    /api/catalog/categories/{id}           (admin, update completo, version obrigatório)
DELETE /api/catalog/categories/{id}           (admin, hard delete, bloqueado se houver produto ativo vinculado)
```

## Paginação e ordenação
- Toda listagem usa `Pageable`/`Page<T>` (convenção global do CLAUDE.md
  raiz) — produtos e categorias.
- Ordenação padrão de ambas: `createdAt DESC`.
- `page`/`size` inválidos (negativos, `size <= 0`) → 400.

## Idempotência
- `POST /products` e `POST /categories` aceitam header opcional
  `Idempotency-Key` (UUID gerado pelo cliente). Repetir a mesma chave
  retorna o recurso já criado (200) em vez de duplicar; primeira chamada
  retorna 201.
- Chaves ficam em uma tabela `idempotency_keys` no próprio schema do
  serviço (catalog), com TTL de 24h — expiradas deixam de deduplicar.
- `PUT` não usa idempotency key — a proteção contra escrita concorrente é
  o optimistic locking (`version`).

## Contrato de erros (HTTP status)
| Status | Quando |
|---|---|
| 200 | GET/PUT com sucesso, ou retry de POST idempotente |
| 201 | POST criando novo recurso (com `Location`) |
| 204 | DELETE com sucesso — inclui repetição idempotente em produto já inativo |
| 400 | payload malformado, campo com tipo/formato inválido, UUID mal formado no path |
| 404 | id de produto/categoria inexistente; `GET /products/{id}` de produto **inativo** também retorna 404 |
| 409 | `version` desatualizado em PUT (optimistic locking) |
| 422 | regra de negócio violada: preço negativo, `categoriaId` inexistente, DELETE de categoria com produto ativo vinculado |

## Regras de negócio (usecase)
- Preço: `BigDecimal`, `>= 0` — validado na usecase (não é constraint de
  banco), retorna 422.
- `categoriaId` deve referenciar uma Categoria existente — validado na
  usecase antes de persistir; 422 se não existir.
- DELETE de produto é sempre soft (`ativo=false`); produto nunca é
  removido do banco.
- DELETE de categoria é hard delete, mas bloqueado (422) se existir ao
  menos um produto **ativo** com aquele `categoriaId` — validação na
  usecase, não apenas FK no banco (produtos inativos vinculados não
  bloqueiam).
- Nome de produto duplicado é permitido.
- Categoria é plana (sem hierarquia/subcategoria).

## Critérios de aceite
- [ ] Produto tem: id, nome, descrição, preço, categoriaId, ativo,
      createdAt, updatedAt, version.
- [ ] Categoria tem: id, nome, createdAt, updatedAt, version.
- [ ] Preço: BigDecimal, nunca negativo (zero permitido).
- [ ] Delete de produto é soft (ativo=false); produto nunca some do banco.
- [ ] Delete de categoria é hard delete, bloqueado (422) se houver produto
      ativo vinculado — validado na usecase.
- [ ] Listagem de produtos: apenas ativos por padrão, paginada
      (Pageable/Page<T>), ordenada por createdAt DESC.
- [ ] Listagem de categorias: paginada (Pageable/Page<T>), ordenada por
      createdAt DESC.
- [ ] GET /products/{id} de produto inativo retorna 404.
- [ ] PUT de produto e PUT de categoria usam optimistic locking (campo
      version) — version desatualizado retorna 409.
- [ ] POST de produto e categoria aceitam Idempotency-Key opcional.
- [ ] categoriaId inexistente em create/update de produto retorna 422.
- [ ] Erros de formato/payload retornam 400; erros de regra de negócio
      retornam 422; recurso inexistente retorna 404.

## Fora do escopo
- Estoque (inventory-service)
- Imagens de produto (fase futura)
- Autenticação (auth-service — fase 3). Endpoints marcados "(admin)" não
  têm enforcement real nesta fase — o contrato já reserva o papel, a
  validação de JWT/role fica para quando auth-service e api-gateway
  tiverem essa camada.
- Hierarquia de categorias (subcategorias) — categorias são planas.
- Publicação de eventos (ProductUpdated) — spec futura, ver ADR 003.
- Update parcial (PATCH) — PUT resolve os casos desta fase; reduz
  superfície de contrato e de testes.
