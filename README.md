# commerce-hub

Plataforma de e-commerce baseada em microserviços.

## Arquitetura

- **10 microserviços** Spring Boot independentes, cada um dono de um bounded context, sob um único reactor Maven (`pom.xml`).
- **Stack**: Java 21 · Spring Boot 3.3.4 · Spring Cloud 2023.0.3 · PostgreSQL (um schema por serviço) · Kafka · Docker.
- **Comunicação entre serviços**: Feign (síncrono) quando o chamador depende da resposta para continuar; eventos Kafka (assíncrono, com `eventId` para idempotência) quando é só notificação.
- **Estrutura interna**: serviços de negócio seguem Clean Architecture (`domain/` → `application/` → `adapter/`); `discovery-service` e `api-gateway` são infraestrutura pura, sem domínio.
- Decisões e trade-offs registrados em [`.adr/`](.adr).

## Serviços

| Serviço               | Porta | Dono de |
| ---------------------- | ----- | ------- |
| discovery-service       | 8761  | registro de serviços (Eureka) |
| api-gateway             | 8080  | roteamento e validação de JWT |
| auth-service            | 8081  | credenciais e papéis |
| catalog-service         | 8082  | produto, categoria |
| inventory-service       | 8083  | estoque por productId |
| customer-service        | 8084  | perfil, endereço |
| cart-service            | 8085  | carrinho (rascunho) |
| order-service           | 8086  | pedido, item de pedido |
| payment-service         | 8087  | transação de pagamento |
| notification-service    | 8088  | templates e envio |

## Build

```
mvn install
```

Mais detalhes de build e convenções de desenvolvimento: [`CLAUDE.md`](CLAUDE.md).
