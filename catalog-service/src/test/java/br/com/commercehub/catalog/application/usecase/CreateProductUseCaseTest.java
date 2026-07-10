package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.CategoryRepository;
import br.com.commercehub.catalog.application.port.IdempotencyKeyStore;
import br.com.commercehub.catalog.application.port.IdempotencyKeyStore.IdempotencyKeyRecord;
import br.com.commercehub.catalog.application.port.ProductRepository;
import br.com.commercehub.catalog.domain.exception.DuplicateRequestInProgressException;
import br.com.commercehub.catalog.domain.exception.InvalidCategoryException;
import br.com.commercehub.catalog.domain.exception.InvalidPriceException;
import br.com.commercehub.catalog.domain.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateProductUseCaseTest {

    private static final UUID CATEGORY_ID = UUID.randomUUID();

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private IdempotencyKeyStore idempotencyKeyStore;

    @InjectMocks
    private CreateProductUseCase useCase;

    @BeforeEach
    void categoryExistsByDefault() {
        lenient().when(categoryRepository.existsById(CATEGORY_ID)).thenReturn(true);
        lenient().when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void negativePriceThrowsInvalidPriceException() {
        CreateProductCommand command = command(new BigDecimal("-0.01"));

        assertThatThrownBy(() -> useCase.execute(command, null))
            .isInstanceOf(InvalidPriceException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void unknownCategoryThrowsInvalidCategoryException() {
        UUID unknownCategory = UUID.randomUUID();
        when(categoryRepository.existsById(unknownCategory)).thenReturn(false);
        CreateProductCommand command = new CreateProductCommand("Café", "Torra média", new BigDecimal("29.90"), unknownCategory);

        assertThatThrownBy(() -> useCase.execute(command, null))
            .isInstanceOf(InvalidCategoryException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void withoutIdempotencyKeyAlwaysCreates() {
        CreationResult<Product> result = useCase.execute(command(new BigDecimal("29.90")), null);

        assertThat(result.created()).isTrue();
        assertThat(result.resource().name()).isEqualTo("Café");
        assertThat(result.resource().active()).isTrue();
        verify(productRepository).save(any());
        verify(idempotencyKeyStore, never()).tryInsert(any(), any(), any(), any(), any());
    }

    @Test
    void newKeyWinsTheRaceThenCreatesAndResolves() {
        UUID key = UUID.randomUUID();
        when(idempotencyKeyStore.tryInsert(eq(key), eq("POST"), eq("PRODUCT"), any(), any())).thenReturn(true);

        CreationResult<Product> result = useCase.execute(command(new BigDecimal("29.90")), key);

        assertThat(result.created()).isTrue();
        verify(productRepository).save(any());
        verify(idempotencyKeyStore).markResolved(key, result.resource().id(), 201);
    }

    @Test
    void alreadyResolvedKeyReturnsExistingResourceWithoutCreatingAgain() {
        UUID key = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Product existing = product(productId);
        when(idempotencyKeyStore.tryInsert(any(), any(), any(), any(), any())).thenReturn(false);
        when(idempotencyKeyStore.findByKey(key)).thenReturn(Optional.of(record(productId, OffsetDateTime.now().plusHours(1))));
        when(productRepository.findById(productId)).thenReturn(Optional.of(existing));

        CreationResult<Product> result = useCase.execute(command(new BigDecimal("29.90")), key);

        assertThat(result.created()).isFalse();
        assertThat(result.resource()).isEqualTo(existing);
        verify(productRepository, never()).save(any());
    }

    @Test
    void claimedExpiredKeyCreatesANewResource() {
        UUID key = UUID.randomUUID();
        when(idempotencyKeyStore.tryInsert(any(), any(), any(), any(), any())).thenReturn(false);
        when(idempotencyKeyStore.findByKey(key))
            .thenReturn(Optional.of(record(UUID.randomUUID(), OffsetDateTime.now().minusHours(1))));
        when(idempotencyKeyStore.tryClaimExpired(eq(key), any(), any(), any())).thenReturn(true);

        CreationResult<Product> result = useCase.execute(command(new BigDecimal("29.90")), key);

        assertThat(result.created()).isTrue();
        verify(productRepository).save(any());
        verify(idempotencyKeyStore).markResolved(key, result.resource().id(), 201);
    }

    @Test
    void expiredKeyClaimedByAnotherRequestThrowsDuplicateRequestInProgress() {
        UUID key = UUID.randomUUID();
        when(idempotencyKeyStore.tryInsert(any(), any(), any(), any(), any())).thenReturn(false);
        when(idempotencyKeyStore.findByKey(key))
            .thenReturn(Optional.of(record(UUID.randomUUID(), OffsetDateTime.now().minusHours(1))));
        when(idempotencyKeyStore.tryClaimExpired(eq(key), any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(command(new BigDecimal("29.90")), key))
            .isInstanceOf(DuplicateRequestInProgressException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void inFlightKeyThrowsDuplicateRequestInProgressWithoutCreating() {
        UUID key = UUID.randomUUID();
        when(idempotencyKeyStore.tryInsert(any(), any(), any(), any(), any())).thenReturn(false);
        when(idempotencyKeyStore.findByKey(key))
            .thenReturn(Optional.of(record(null, OffsetDateTime.now().plusHours(1))));

        assertThatThrownBy(() -> useCase.execute(command(new BigDecimal("29.90")), key))
            .isInstanceOf(DuplicateRequestInProgressException.class);

        verify(productRepository, never()).save(any());
        verify(idempotencyKeyStore, never()).markResolved(any(), any(), anyInt());
    }

    @Test
    void duplicateKeyThatVanishedBeforeTheReadThrowsDuplicateRequestInProgress() {
        UUID key = UUID.randomUUID();
        when(idempotencyKeyStore.tryInsert(any(), any(), any(), any(), any())).thenReturn(false);
        when(idempotencyKeyStore.findByKey(key)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(command(new BigDecimal("29.90")), key))
            .isInstanceOf(DuplicateRequestInProgressException.class);

        verify(productRepository, never()).save(any());
    }

    private static CreateProductCommand command(BigDecimal price) {
        return new CreateProductCommand("Café", "Torra média", price, CATEGORY_ID);
    }

    private static Product product(UUID id) {
        OffsetDateTime now = OffsetDateTime.now();
        return new Product(id, "Café", "Torra média", new BigDecimal("29.90"), CATEGORY_ID, true, now, now, 0L);
    }

    private static IdempotencyKeyRecord record(UUID resourceId, OffsetDateTime expiresAt) {
        Integer status = resourceId == null ? null : 201;
        return new IdempotencyKeyRecord(UUID.randomUUID(), "POST", "PRODUCT", resourceId, status,
            expiresAt.minusHours(24), expiresAt);
    }
}
