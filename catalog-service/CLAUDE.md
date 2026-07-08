# catalog-service — Contexto local

## Responsabilidade única
CRUD de Produto e Categoria do catálogo.

## Bounded context
Conhece: Produto, Categoria.
NÃO conhece: estoque (inventory-service), preço pago/pedido (order-service),
carrinho (cart-service), pagamento (payment-service). Referências a Produto
em outros serviços são sempre por `productId` — nunca uma cópia do Produto.

## Schema PostgreSQL
`catalog` — nunca acessar outros schemas.

## Dependências de outros serviços
Nenhuma. catalog-service nunca chama outro serviço de forma síncrona (é
sempre o lado chamado — ADR 003) e não publica/consome eventos nesta fase.
