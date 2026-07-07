# ADR 004: Mapa de Bounded Contexts

**Data:** {hoje}
**Status:** Aceito

## Contexto
Com 9 serviços, a fronteira do que cada um conhece precisa ser
explícita ANTES do código. Sem isso, responsabilidades vazam:
o catálogo ganha campo de estoque, o pedido conhece detalhes de
pagamento, e a plataforma vira um monolito distribuído.

## Decisão — o que cada serviço conhece e NÃO conhece

| Serviço | Conhece | NÃO conhece |
|---|---|---|
| auth-service | Usuário (credenciais, papel) | Perfil, pedidos, qualquer negócio |
| catalog-service | Produto, Categoria | Estoque, preço promocional por cliente |
| inventory-service | Estoque por productId | O produto em si (nome, descrição) |
| customer-service | Perfil, endereço | Pedidos, credenciais |
| cart-service | Carrinho (rascunho) | Persistência de pedido |
| order-service | Pedido, ItemPedido | Detalhes de pagamento, dados do cartão |
| payment-service | Transação de pagamento | Composição do pedido |
| notification-service | Templates e envio | Qualquer regra de negócio |
| api-gateway | Rotas, validação JWT | Não tem banco, não tem domínio |
| discovery-service | Registro de serviços | Não tem lógica de negócio |

## Regras derivadas
1. Referência entre contextos é sempre por ID (inventory guarda
   productId, nunca uma cópia do Produto).
2. Nenhum serviço acessa schema de outro (reforça ADR 002).
3. Mudança nessas fronteiras exige atualizar este ADR primeiro,
   código depois — nunca o contrário.

## Consequências
+ Cada fronteira vira o bloco "Bounded context" do CLAUDE.md
  local do serviço — o Claude Code respeita a fronteira em
  toda sessão automaticamente
+ Discussões de "onde colocar campo X" apontam para este documento
- Consultas que cruzam contextos exigem composição (Feign/eventos),
  nunca JOIN — é o preço do isolamento