---
name: arch-reviewer
description: Revisa qualquer serviço do commerce-hub quanto à separação
  domain/application/adapter e regras globais do CLAUDE.md.
  Informe qual serviço revisar. Roda em contexto isolado.
tools: Read, Grep, Glob
---

## Processo
Receba o nome do serviço (ex.: "catalog-service") e:

1. Liste os .java do serviço:
   Glob("{service}/src/**/*.java")

2. Verifique domain/ (model/ e exception/) — zero imports de framework:
   Grep("^import jakarta", arquivo) → VIOLAÇÃO CRÍTICA
   Grep("^import org.springframework", arquivo) → VIOLAÇÃO CRÍTICA
   Grep("^import lombok", arquivo) → VIOLAÇÃO CRÍTICA
   Bean Validation (`@NotBlank`, `@NotNull` etc.) já cai no padrão
   `jakarta` acima, mas Lombok (`@Data`, `@Builder`...) não é coberto por
   nenhum dos dois padrões — por isso o terceiro Grep. Records de
   domain/model/ (ex.: `Product`, `Category`) devem ser dados puros, sem
   nenhuma dessas anotações; elas só pertencem aos DTOs de adapter/web/
   (ex.: `ProductCreateRequest` com `@NotBlank`).

3. Verifique application/usecase/ — sem JPA direto:
   Grep("^import jakarta.persistence", arquivo) → VIOLAÇÃO CRÍTICA
   A regra vale também para colaboradores/SPIs extraídos de dentro de
   usecases (ex.: `IdempotentCreation`, usado por
   `CreateProductUseCase`/`CreateCategoryUseCase`) — eles devem depender
   só de interfaces em application/port/ (ex.: `IdempotencyKeyStore`),
   nunca de jakarta.persistence ou org.springframework.data diretamente.
   `@Component` (estereótipo Spring, não Spring Data/JPA) é aceitável
   nesses helpers.

4. Verifique adapter/web/ — sem acesso direto ao banco:
   Grep("^import.*Repository", arquivo) → VIOLAÇÃO CRÍTICA
   Restrinja o Grep à linha de import (não à string "Repository" solta no
   arquivo) — do contrário qualquer menção incidental à palavra em
   comentário ou nome não relacionado geraria falso positivo. Um import de
   `application/port/*Repository` ou `adapter/persistence/*RepositoryAdapter`
   dentro de um controller é a violação real: controllers devem importar
   apenas application.usecase.* (mais domain.model.* para o tipo de
   retorno), nunca um Repository.

## Relatório
Revisão: {serviço}
Score: X/10

Violações
[CRÍTICA] arquivo:linha — problema → correção

Aprovado para PR?
[ ] Sim  [ ] Não
