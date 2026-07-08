---
name: jpa-schema
description: Use esta skill sempre que for criar entidades JPA, migrations
  Flyway, ou configurar isolamento de schema PostgreSQL em um serviço de
  negócio do commerce-hub (auth, catalog, inventory, customer, cart, order,
  payment, notification). Assume que o serviço já existe — para criar o
  esqueleto do zero use primeiro a skill microservice-scaffold. Não se
  aplica a discovery-service/api-gateway (sem banco, ver ADR 004).
---

## Quando não se aplica

- `discovery-service` e `api-gateway` não têm banco de dados nem `adapter/
  persistence` (ADR 004) — esta skill não se aplica a eles.
- Para criar o serviço do zero (pom.xml, application.yml, estrutura de
  pastas), use a skill `microservice-scaffold` primeiro. Aqui assume-se que
  o serviço (Trilha A) já existe e o objetivo é adicionar/alterar uma
  entidade ou tabela.

## Schema isolation (ADR 002)

Um cluster PostgreSQL, um schema por serviço. Nunca acessar schema de outro
serviço (ver CLAUDE.md raiz e ADR 004 — sem JOIN entre schemas de serviços
diferentes; composição entre serviços é via Feign ou eventos).

```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_schema: catalog   # nome do schema = nome do bounded context
    hibernate:
      ddl-auto: validate          # nunca create/update — Flyway é dono do schema
  flyway:
    schemas: catalog
    locations: classpath:db/migration
```

`ddl-auto: validate` é o padrão do projeto (ver `microservice-scaffold`) —
Hibernate nunca cria nem altera tabelas, só valida que as entidades batem
com o schema que o Flyway já aplicou.

## Migrations Flyway (obrigatório)

Nunca `ddl-auto: create`/`update`. Toda mudança de schema é uma migration
versionada em `src/main/resources/db/migration/`:

```
src/main/resources/db/migration/
├── V1__create_categories.sql
└── V2__create_products.sql
```

- Nome: `V{n}__descricao_em_snake_case.sql`, `n` incremental **por
  serviço** — cada serviço tem seu próprio histórico, já que
  `spring.flyway.schemas` isola a tabela `flyway_schema_history` dentro do
  schema do próprio serviço.
- Migration já aplicada nunca é editada. Schema precisa mudar depois? Cria-
  se `V{n+1}__...sql`, nunca se reescreve uma anterior.
- Tipos de coluna refletem as convenções globais do CLAUDE.md raiz: `id
  UUID`, dinheiro `NUMERIC(10,2)` (⇄ `BigDecimal`), datas `TIMESTAMP WITH
  TIME ZONE` (⇄ `OffsetDateTime` — nunca `TIMESTAMP` sem timezone).

```sql
-- V2__create_products.sql
CREATE TABLE products (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(10,2) NOT NULL,
    category_id UUID NOT NULL REFERENCES categories(id),
    active BOOLEAN NOT NULL DEFAULT true
);
```

## Entidade JPA (adapter/persistence/)

Fica em `adapter/persistence/`, nunca é exposta diretamente na API
(controllers usam DTOs record — ver CLAUDE.md raiz). `@ManyToOne`/
`@JoinColumn` só entre tabelas do **mesmo schema/serviço**; referência a
OUTRO bounded context é sempre `UUID` cru (ADR 004), nunca uma associação
JPA.

```java
@Entity
@Table(name = "products") // schema vem do default_schema, não repetir aqui
public class ProductEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private CategoryEntity category; // OK: category é do MESMO schema (catalog)

    public Product toDomain() { ... }
    public static ProductEntity fromDomain(Product p) { ... }
}
```

ID gerado pelo banco/Hibernate? Use `@GeneratedValue(strategy =
GenerationType.UUID)`. ID gerado na `usecase` (ex.: `UUID.randomUUID()`
antes de persistir)? Não usar `@GeneratedValue` — a entidade só recebe o ID
já pronto.

## Domain model (domain/model/) — sem JPA

Sem nenhuma anotação de framework (nem `@Entity`, nem Lombok) — record puro,
mapeado só via `toDomain()`/`fromDomain()` na entidade JPA.

```java
public record Product(
    UUID id, String name, String description,
    BigDecimal price, UUID categoryId, boolean active
) {}
```

Mesmo quando a referência é para uma entidade do mesmo serviço (ex.:
`Category` dentro de catalog-service), o domain model usa o ID cru
(`categoryId`), não o objeto relacionado — mantém o record desacoplado do
grafo de entidades JPA. Para outro bounded context o ID cru é obrigatório
(ADR 004): nunca `@ManyToOne`/`@JoinColumn` cruzando schemas.
