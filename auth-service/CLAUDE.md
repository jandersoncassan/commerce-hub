# auth-service — Contexto local

## Responsabilidade única
Cadastro público (self-service) e autenticação de usuários. Único
emissor de JWT da plataforma.

## Bounded context
Conhece: Usuário (credenciais e papel — `ROLE_CUSTOMER`/`ROLE_ADMIN`).
NÃO conhece: perfil, endereço (customer-service), pedidos, pagamento, ou
qualquer outro dado de negócio. Referências a um usuário em outros
serviços são sempre por `userId` — nunca uma cópia do Usuário.

## Schema PostgreSQL
`auth` — nunca acessar outros schemas.

## Dependências de outros serviços
Nenhuma. auth-service nunca chama outro serviço de forma síncrona (é
sempre o lado chamado) e não publica/consome eventos nesta fase. Quem
valida o JWT que este serviço emite é sempre o api-gateway (ADR 005) —
auth-service não depende disso para funcionar, e não valida token
próprio nem alheio (ver skill `spring-security-jwt`).
