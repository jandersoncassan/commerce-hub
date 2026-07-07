# ADR 002: PostgreSQL único com schemas por serviço

**Data:** 2026-07-06
**Status:** Aceito

## Contexto
Railway free tier limita instâncias de banco. 9 PostgreSQL separados
esgotariam o crédito.

## Decisão
Um cluster PostgreSQL com um schema por serviço.
Cada serviço acessa APENAS seu próprio schema via configuração
de datasource com `spring.jpa.properties.hibernate.default_schema`.

## Consequências
+ Custo zero de banco no Railway
+ Isolamento lógico mantido — nenhum serviço acessa schema alheio
- Isolamento físico inexistente (aceitável para aprendizado)
- Migração futura requer mover dados entre bancos (quando necessário)