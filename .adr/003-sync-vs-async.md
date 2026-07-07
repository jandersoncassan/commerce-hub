# ADR 003: Comunicação síncrona vs assíncrona entre serviços

**Data:** 2026-07-06
**Status:** Aceito

## Contexto
Com 9 serviços, cada interação entre eles exige uma escolha:
chamada síncrona (Feign/REST via Eureka) ou evento assíncrono
(Kafka). Escolher errado gera acoplamento temporal desnecessário
(síncrono demais) ou complexidade prematura (assíncrono demais).

## Decisão
Usar o critério de necessidade de resposta imediata:

**Síncrono (Feign Client)** quando o chamador PRECISA da resposta
para continuar seu próprio fluxo:
- cart-service → inventory-service: "tem estoque?" (checkout não
  avança sem essa resposta)
- order-service → inventory-service: "reserva o estoque" (precisa
  saber se conseguiu antes de prosseguir)
- ai/qualquer-service → catalog-service: consulta de produto

**Assíncrono (eventos Kafka)** quando o chamador só precisa
NOTIFICAR que algo aconteceu, sem depender da resposta:
- cart-service publica CheckoutRequested → order-service consome
- order-service publica OrderCreated → notification-service consome
- catalog-service publica ProductUpdated → quem quiser consome
  (ex: recommendation-service na fase 7)

## Regras derivadas
1. Todo Feign Client tem fallback obrigatório (skill feign-client).
2. Todo evento carrega eventId para idempotência no consumer
   (skill event-publisher).
3. Notificação NUNCA é síncrona — falha no e-mail não pode
   derrubar a criação do pedido.
4. Na dúvida, comece síncrono (mais simples de debugar) e migre
   para evento quando o acoplamento temporal doer.

## Consequências
+ Critério objetivo elimina discussão caso a caso
+ Serviços de notificação/projeção desacoplados naturalmente
+ Cada padrão tem sua skill correspondente garantindo consistência
- Dois mecanismos de comunicação = dois conjuntos de falha
  para monitorar (timeout/circuit breaker vs DLQ/retry)
- Eventos exigem raciocínio de consistência eventual
  (o pedido existe antes da notificação sair)