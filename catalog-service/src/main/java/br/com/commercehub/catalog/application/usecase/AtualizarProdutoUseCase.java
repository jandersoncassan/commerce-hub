package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.CategoriaRepository;
import br.com.commercehub.catalog.application.port.ProdutoRepository;
import br.com.commercehub.catalog.domain.exception.ProdutoNaoEncontradoException;
import br.com.commercehub.catalog.domain.model.Produto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Atualização total (PUT) de produto, com detecção de conflito por optimistic locking.
 *
 * <p>O {@code version} que chega no comando é o que o cliente leu no GET anterior, e é ele
 * — não o da linha atual — que precisa ir para o {@code save}. O produto montado aqui é
 * uma cópia <em>detached</em>: o Hibernate compara esse {@code version} contra o valor da
 * coluna no UPDATE e lança {@code ObjectOptimisticLockingFailureException} se divergirem.
 * Carregar a entidade managed e sobrescrever o {@code version} dela não funcionaria — ela
 * usa o valor que carregou do banco, e a atribuição posterior é ignorada (nota da seção 7
 * do plan.md).
 */
@Service
@Transactional
public class AtualizarProdutoUseCase {

    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;

    public AtualizarProdutoUseCase(ProdutoRepository produtoRepository, CategoriaRepository categoriaRepository) {
        this.produtoRepository = produtoRepository;
        this.categoriaRepository = categoriaRepository;
    }

    public Produto executar(AtualizarProdutoCommand comando) {
        ProdutoValidacao.validar(comando.preco(), comando.categoriaId(), categoriaRepository);

        Produto existente = produtoRepository.findById(comando.id())
            .orElseThrow(() -> new ProdutoNaoEncontradoException("Produto não encontrado: " + comando.id()));

        return produtoRepository.save(new Produto(
            existente.id(), comando.nome(), comando.descricao(), comando.preco(), comando.categoriaId(),
            existente.ativo(), existente.createdAt(), OffsetDateTime.now(), comando.version()
        ));
    }
}
