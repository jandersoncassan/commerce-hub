package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.ProdutoRepository;
import br.com.commercehub.catalog.domain.exception.ProdutoNaoEncontradoException;
import br.com.commercehub.catalog.domain.model.Produto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DELETE soft de produto: a linha permanece na tabela e só {@code ativo} vira {@code false}.
 * Produtos inativos somem das listagens, mas continuam referenciáveis por {@code productId}
 * a partir de outros serviços (ADR 004).
 *
 * <p>Desativar um produto já inativo é um no-op de verdade — retorna antes de chegar ao
 * {@code save}. Como {@code ProdutoRepositoryAdapter.save} faz {@code saveAndFlush}, um
 * {@code save} incondicional emitiria um UPDATE e bumparia {@code updatedAt}/{@code version}
 * mesmo sem mudança de estado, quebrando a idempotência que o DELETE promete.
 */
@Service
@Transactional
public class DesativarProdutoUseCase {

    private final ProdutoRepository produtoRepository;

    public DesativarProdutoUseCase(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }

    public void executar(UUID id) {
        Produto existente = produtoRepository.findById(id)
            .orElseThrow(() -> new ProdutoNaoEncontradoException("Produto não encontrado: " + id));

        if (!existente.ativo()) {
            return;
        }

        produtoRepository.save(new Produto(
            existente.id(), existente.nome(), existente.descricao(), existente.preco(),
            existente.categoriaId(), false, existente.createdAt(), OffsetDateTime.now(),
            existente.version()
        ));
    }
}
