# CLAUDE.md

Este arquivo fornece orientações ao Claude Code (claude.ai/code) ao trabalhar com código neste repositório.

## Projeto
commerce-hub é um projeto de aprendizado: plataforma de e-commerce modular com 10 microserviços independentes (Clean Architecture + hexagonal por serviço), usada como veículo para praticar Claude Code
Skills, SDD e Agents em múltiplos bounded contexts isolados.

## Arquitetura (a partir de `.adr/`)

A plataforma é composta por 10 microserviços Spring Boot independentes (Java 21, Spring Boot 3.3.4, Spring Cloud 2023.0.3) sob um único reactor Maven (`pom.xml`, packaging `pom`):

- `discovery-service` — registro de serviços Eureka, sem lógica de negócio
- `api-gateway` — apenas roteamento + validação de JWT, sem banco de dados, sem lógica de domínio
- `auth-service` — apenas credenciais e papéis (roles)
- `catalog-service` — Produto, Categoria
- `inventory-service` — apenas estoque por `productId` (sem detalhes do produto)
- `customer-service` — perfil, endereço
- `cart-service` — apenas carrinho (rascunho), sem persistência de pedido
- `order-service` — Pedido, ItemPedido
- `payment-service` — apenas transações de pagamento (sem composição do pedido)
- `notification-service` — apenas templates e envio, sem regras de negócio

### Regras de bounded context (ADR 004)

- Referências entre serviços são sempre por ID (ex.: inventory guarda `productId`, nunca uma cópia do Produto). Nunca faça JOIN entre schemas de serviços diferentes — componha via Feign ou eventos.
- Nenhum serviço acessa o schema de outro serviço.
- Se uma fronteira precisar mudar, atualize o ADR 004 primeiro, o código depois — nunca o contrário.

### Dados (ADR 002)

Um único cluster PostgreSQL (restrição do free tier do Railway), um schema por serviço. Cada serviço deve restringir seu datasource apenas ao próprio schema via `spring.jpa.properties.hibernate.default_schema`. Nenhum serviço pode acessar o schema de outro.

### Comunicação síncrona vs. assíncrona (ADR 003)

Critério de decisão: o chamador precisa da resposta para continuar seu próprio fluxo?

- **Síncrono (Feign Client via Eureka)** quando o chamador precisa da resposta para prosseguir — ex.: cart-service → inventory-service ("tem estoque?"), order-service → inventory-service ("reserva o estoque"), qualquer serviço → catalog-service (consulta de produto). Todo Feign Client deve ter um fallback.
- **Assíncrono (eventos Kafka)** quando o chamador só precisa notificar, sem bloquear — ex.: cart-service publica `CheckoutRequested`, order-service publica `OrderCreated`, catalog-service publica `ProductUpdated`. Todo evento carrega um `eventId` para idempotência no consumer. Notificação nunca deve ser síncrona — uma falha de e-mail nunca pode bloquear a criação do pedido.
- Na dúvida, comece síncrono (mais simples de debugar) e migre para eventos somente quando o acoplamento temporal realmente incomodar.

## Stack global
- Java 21, Spring Boot 3.3.x, Maven (monorepo)
- Spring Cloud: Eureka (discovery) + Gateway (api-gateway)
- PostgreSQL 16, um schema por serviço
- Docker + Docker Compose
- Deploy: Railway.app
- Kafka (eventos assíncronos)

## Pacote base por serviço
`br.com.commercehub.{nome-do-servico}`
Exemplo: `br.com.commercehub.catalog`

## Estrutura obrigatória por serviço (Clean Architecture)

```
{service}/src/main/java/br/com/commercehub/{service}/
├── domain/
│   ├── model/       ← entidades de domínio (sem anotações de framework)
│   └── exception/   ← exceções de negócio
├── application/
│   ├── port/        ← interfaces (repositórios, serviços externos)
│   └── usecase/     ← lógica de negócio, orquestra domain + ports
└── adapter/
    ├── persistence/  ← JPA entities, repositories Spring Data
    ├── web/          ← REST controllers, DTOs de request/response
    └── messaging/    ← publishers/consumers de eventos (quando aplicável)
```

## Portas por serviço

| Serviço               | Porta |
| --------------------- | ----- |
| discovery-service     | 8761  |
| api-gateway           | 8080  |
| auth-service          | 8081  |
| catalog-service       | 8082  |
| inventory-service     | 8083  |
| customer-service      | 8084  |
| cart-service          | 8085  |
| order-service         | 8086  |
| payment-service       | 8087  |
| notification-service  | 8088  |

## Convenções globais obrigatórias
- IDs: UUID. Nunca Long sequencial exposto em API.
- Dinheiro: BigDecimal. Nunca Double.
- Datas: OffsetDateTime. Nunca LocalDateTime para persistência.
- DTOs: Records Java 21. Nunca expor entidade JPA diretamente.
- Senhas: BCrypt strength 12.
- Paginação: toda listagem recebe Pageable e retorna Page<T>.
- Schema JPA: cada serviço usa seu próprio schema PostgreSQL.

## Comunicação entre serviços
- Síncrona: Feign Client (via api-gateway ou direto pelo Eureka)
- Assíncrona: eventos Kafka (fase 5 em diante)
- Nunca acessar banco de outro serviço diretamente.
- Nunca chamar outro serviço de dentro do domain/.

## O que NÃO fazer (global)
- Lógica de negócio em @RestController
- Injetar Repository direto em Controller
- Importar classes de outro serviço (exceto contratos compartilhados)
- Chamar outro serviço de dentro de domain/ ou application/port/
- Commitar sem testes passando

## Comandos

Java 21, reactor multi-módulo Maven.

- Subir infra:      `docker-compose up -d postgres kafka`
- Subir tudo:       `docker-compose up --build`
- Build tudo:       `mvn install` (ou `mvn clean package -DskipTests` para pular os testes)
- Build 1 serviço:  `mvn install -pl <module-name> -am` (ou `mvn clean package -pl <module-name> -DskipTests` para pular os testes)
- Testes:           `mvn test -pl <module-name>`

Ainda não há ferramental de teste/lint no nível raiz além do Maven padrão (sem configuração de CI, sem linters configurados). À medida que módulos forem adicionados, prefira comandos Maven escopados por módulo em vez de um build completo do reactor, para iteração mais rápida.
