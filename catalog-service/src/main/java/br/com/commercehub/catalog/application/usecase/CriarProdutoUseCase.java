package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.CategoriaRepository;
import br.com.commercehub.catalog.application.port.IdempotencyKeyStore;
import br.com.commercehub.catalog.application.port.IdempotencyKeyStore.IdempotencyKeyRecord;
import br.com.commercehub.catalog.application.port.ProdutoRepository;
import br.com.commercehub.catalog.domain.exception.ProdutoNaoEncontradoException;
import br.com.commercehub.catalog.domain.exception.RequisicaoDuplicadaEmAndamentoException;
import br.com.commercehub.catalog.domain.model.Produto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Criação de produto com deduplicação por {@code Idempotency-Key}, seguindo a
 * estratégia grava-primeiro da seção 8 do plan.md: a chave é inserida antes do
 * recurso existir, e é o próprio banco (PK única) que decide qual requisição
 * concorrente ganha a corrida.
 *
 * <p>Contrapartida conhecida: {@link IdempotencyKeyStore#tryInsert} commita em
 * transação própria (REQUIRES_NEW), então se a criação do produto falhar depois,
 * a linha da chave sobrevive com {@code resourceId} nulo e aquela chave passa a
 * responder 409 até o TTL de 24h expirar. É o preço de fechar a janela de
 * duplicação — inserir a chave na mesma transação do produto reabriria o cenário
 * "busca → não achou → processa" com dois recursos criados.
 */
@Service
@Transactional
public class CriarProdutoUseCase {

    private static final String METODO_HTTP = "POST";
    private static final String TIPO_RECURSO = "PRODUCT";
    private static final int TTL_HORAS = 24;
    private static final int STATUS_CRIADO = 201;

    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;
    private final IdempotencyKeyStore idempotencyKeyStore;

    public CriarProdutoUseCase(ProdutoRepository produtoRepository, CategoriaRepository categoriaRepository,
                                IdempotencyKeyStore idempotencyKeyStore) {
        this.produtoRepository = produtoRepository;
        this.categoriaRepository = categoriaRepository;
        this.idempotencyKeyStore = idempotencyKeyStore;
    }

    /**
     * @param idempotencyKey valor do header {@code Idempotency-Key}, ou {@code null}
     *                       quando o cliente não o enviou (sem deduplicação).
     */
    public ResultadoCriacao<Produto> executar(CriarProdutoCommand comando, UUID idempotencyKey) {
        ProdutoValidacao.validar(comando.preco(), comando.categoriaId(), categoriaRepository);

        if (idempotencyKey == null) {
            return ResultadoCriacao.novo(criar(comando, OffsetDateTime.now()));
        }

        OffsetDateTime agora = OffsetDateTime.now();
        if (idempotencyKeyStore.tryInsert(idempotencyKey, METODO_HTTP, TIPO_RECURSO, agora, expiracao(agora))) {
            return ResultadoCriacao.novo(criarEResolver(comando, idempotencyKey, agora));
        }

        IdempotencyKeyRecord registro = idempotencyKeyStore.findByKey(idempotencyKey)
            .orElseThrow(() -> emAndamento(idempotencyKey));

        if (expirada(registro, agora)) {
            return ResultadoCriacao.novo(reprocessarChaveExpirada(comando, idempotencyKey, agora));
        }
        if (registro.resourceId() == null) {
            throw emAndamento(idempotencyKey);
        }
        return ResultadoCriacao.existente(buscar(registro.resourceId()));
    }

    /**
     * Chave expirada equivale a chave inexistente (o TTL desativa a deduplicação), mas a
     * linha ainda ocupa a PK — então é preciso reivindicá-la com um UPDATE condicional em vez
     * de reinserir. Perder a reivindicação significa que outra requisição chegou primeiro.
     */
    private Produto reprocessarChaveExpirada(CriarProdutoCommand comando, UUID idempotencyKey, OffsetDateTime agora) {
        if (!idempotencyKeyStore.tryClaimExpired(idempotencyKey, agora, expiracao(agora), agora)) {
            throw emAndamento(idempotencyKey);
        }
        return criarEResolver(comando, idempotencyKey, agora);
    }

    private Produto criarEResolver(CriarProdutoCommand comando, UUID idempotencyKey, OffsetDateTime agora) {
        Produto produto = criar(comando, agora);
        idempotencyKeyStore.markResolved(idempotencyKey, produto.id(), STATUS_CRIADO);
        return produto;
    }

    private Produto criar(CriarProdutoCommand comando, OffsetDateTime agora) {
        return produtoRepository.save(new Produto(
            UUID.randomUUID(), comando.nome(), comando.descricao(), comando.preco(),
            comando.categoriaId(), true, agora, agora, 0L
        ));
    }

    private Produto buscar(UUID produtoId) {
        return produtoRepository.findById(produtoId)
            .orElseThrow(() -> new ProdutoNaoEncontradoException("Produto não encontrado: " + produtoId));
    }

    private static boolean expirada(IdempotencyKeyRecord registro, OffsetDateTime agora) {
        return !registro.expiresAt().isAfter(agora);
    }

    private static OffsetDateTime expiracao(OffsetDateTime agora) {
        return agora.plusHours(TTL_HORAS);
    }

    private static RequisicaoDuplicadaEmAndamentoException emAndamento(UUID idempotencyKey) {
        return new RequisicaoDuplicadaEmAndamentoException(
            "Requisição com a mesma Idempotency-Key já está em processamento: " + idempotencyKey);
    }
}
