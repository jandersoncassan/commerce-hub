# ADR 001: Arquitetura de Microserviços

**Data:** 2026-07-06
**Status:** Aceito

## Contexto
Projeto de aprendizado de Claude Code usando plataforma de e-commerce
como veículo. Queremos praticar Skills, SDD e Agents em múltiplos
contextos isolados.

## Decisão
Usar arquitetura de microserviços com 9 serviços independentes,
cada um com responsabilidade única (Single Responsibility em nível
de serviço — Clean Architecture aplicada ao macrodesign).

## Consequências
+ Cada serviço é um bounded context isolado — fronteira natural para SDD
+ Múltiplos serviços = mais oportunidades de praticar padrões com Claude Code
+ Comunicação entre serviços exige contratos explícitos (APIs, eventos)
- Complexidade de infra maior — mitigada com Docker Compose
- Deploy progressivo necessário no Railway free tier