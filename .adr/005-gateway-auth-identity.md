# ADR 005: Autenticação centralizada no gateway com propagação de identidade via headers

**Data:** 2026-07-14
**Status:** Aceito

## Contexto
Com auth-service emitindo JWT e api-gateway validando, é preciso decidir
se os 8 serviços de negócio (catalog, inventory, customer, cart, order,
payment, notification, e futuros) também precisam entender JWT — seja
para autenticação, seja para saber "quem" fez a chamada quando isso
importa para uma regra de negócio (ex.: order-service confirmando que
um pedido pertence ao customerId autenticado).

## Decisão
O api-gateway é o ÚNICO ponto de autenticação E autorização por
rota/role. Ele valida assinatura e expiração do JWT, e decide se o
role da claim atende ao exigido pela rota (tabela rota→role, seção 6
da skill spring-security-jwt).

Os serviços de negócio internos:
- NÃO recebem spring-security nem qualquer lib JWT como dependência.
- NÃO revalidam o token em nenhuma circunstância.
- Quando precisarem saber "quem" fez a chamada para lógica de negócio
  (não para segurança), recebem essa informação via headers que o
  gateway propaga: `X-User-Id`, `X-User-Roles`.

Fluxo:
```
Cliente → [JWT no Authorization] → api-gateway
  │ valida assinatura + expiração
  │ verifica role da rota
  │
  [X-User-Id, X-User-Roles] → serviço interno
  │
  serviço usa headers só se a REGRA DE NEGÓCIO precisar de
  identidade (não para autenticar)
```

## Consequências
+ "Os serviços internos confiam que o gateway já validou" (CLAUDE.md)
  deixa de ser frase solta e vira mecanismo concreto e único.
+ Autorização não se duplica em 8 lugares diferentes — muda regra de
  rota, muda em UM arquivo (RouteAuthorizationProperties no gateway).
+ Serviços de negócio ficam mais leves (zero dependência de segurança),
  o que é coerente com Trilha A não incluir preocupações transversais.
+ Superfície de ataque menor: só o gateway manipula segredo JWT e
  lógica de validação criptográfica.
- Chamada de serviço-a-serviço (Feign, Fase 4) que não passa pelo
  gateway não carrega identidade automaticamente — se um serviço
  interno precisar propagar "em nome de quem" está chamando outro
  serviço, isso exige repassar os headers explicitamente na chamada
  Feign (decisão futura, quando o primeiro caso real aparecer).
- Headers de identidade não são criptograficamente verificados pelo
  serviço que os recebe — o modelo de confiança pressupõe que só o
  gateway pode alcançar os serviços internos na rede (nenhuma exposição
  direta de porta de serviço de negócio para fora da rede interna).
  Isso é aceitável neste projeto de aprendizado; em produção real
  exigiria rede segregada (VPC interna, service mesh, ou mTLS) para
  a suposição se sustentar. **Atenção na Fase 6 (Railway):** confira
  que nenhuma porta de serviço de negócio fica exposta publicamente —
  só api-gateway deveria ter endpoint acessível de fora.

## Regra derivada
Se algum serviço de negócio algum dia importar uma lib JWT ou tentar
revalidar o token, isso é violação deste ADR — atualiza o ADR primeiro
se a decisão precisar mudar, nunca introduz a exceção em silêncio no
código (mesma regra do ADR 004).
