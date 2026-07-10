package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.CategoryRepository;
import br.com.commercehub.catalog.application.port.IdempotencyKeyStore;
import br.com.commercehub.catalog.application.port.IdempotencyKeyStore.IdempotencyKeyRecord;
import br.com.commercehub.catalog.domain.exception.DuplicateRequestInProgressException;
import br.com.commercehub.catalog.domain.model.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class CreateCategoryUseCaseTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private IdempotencyKeyStore idempotencyKeyStore;

    private CreateCategoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateCategoryUseCase(categoryRepository, new IdempotentCreation(idempotencyKeyStore));
        lenient().when(categoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void withoutIdempotencyKeyAlwaysCreates() {
        CreationResult<Category> result = useCase.execute(new CreateCategoryCommand("Periféricos"), null);

        assertThat(result.created()).isTrue();
        assertThat(result.resource().name()).isEqualTo("Periféricos");
        verify(categoryRepository).save(any());
        verify(idempotencyKeyStore, never()).tryInsert(any(), any(), any(), any(), any());
    }

    /** O `resourceType` gravado precisa ser CATEGORY, não PRODUCT: as duas famílias compartilham a tabela de chaves. */
    @Test
    void newKeyWinsTheRaceThenCreatesAndResolves() {
        UUID key = UUID.randomUUID();
        when(idempotencyKeyStore.tryInsert(eq(key), eq("POST"), eq("CATEGORY"), any(), any())).thenReturn(true);

        CreationResult<Category> result = useCase.execute(new CreateCategoryCommand("Periféricos"), key);

        assertThat(result.created()).isTrue();
        verify(categoryRepository).save(any());
        verify(idempotencyKeyStore).markResolved(key, result.resource().id(), 201);
    }

    @Test
    void alreadyResolvedKeyReturnsExistingResourceWithoutCreatingAgain() {
        UUID key = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Category existing = category(categoryId);
        when(idempotencyKeyStore.tryInsert(any(), any(), any(), any(), any())).thenReturn(false);
        when(idempotencyKeyStore.findByKey(key)).thenReturn(Optional.of(record(categoryId, OffsetDateTime.now().plusHours(1))));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(existing));

        CreationResult<Category> result = useCase.execute(new CreateCategoryCommand("Periféricos"), key);

        assertThat(result.created()).isFalse();
        assertThat(result.resource()).isEqualTo(existing);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void claimedExpiredKeyCreatesANewResource() {
        UUID key = UUID.randomUUID();
        when(idempotencyKeyStore.tryInsert(any(), any(), any(), any(), any())).thenReturn(false);
        when(idempotencyKeyStore.findByKey(key))
            .thenReturn(Optional.of(record(UUID.randomUUID(), OffsetDateTime.now().minusHours(1))));
        when(idempotencyKeyStore.tryClaimExpired(eq(key), any(), any(), any())).thenReturn(true);

        CreationResult<Category> result = useCase.execute(new CreateCategoryCommand("Periféricos"), key);

        assertThat(result.created()).isTrue();
        verify(categoryRepository).save(any());
        verify(idempotencyKeyStore).markResolved(key, result.resource().id(), 201);
    }

    @Test
    void expiredKeyClaimedByAnotherRequestThrowsDuplicateRequestInProgress() {
        UUID key = UUID.randomUUID();
        when(idempotencyKeyStore.tryInsert(any(), any(), any(), any(), any())).thenReturn(false);
        when(idempotencyKeyStore.findByKey(key))
            .thenReturn(Optional.of(record(UUID.randomUUID(), OffsetDateTime.now().minusHours(1))));
        when(idempotencyKeyStore.tryClaimExpired(eq(key), any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(new CreateCategoryCommand("Periféricos"), key))
            .isInstanceOf(DuplicateRequestInProgressException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void inFlightKeyThrowsDuplicateRequestInProgressWithoutCreating() {
        UUID key = UUID.randomUUID();
        when(idempotencyKeyStore.tryInsert(any(), any(), any(), any(), any())).thenReturn(false);
        when(idempotencyKeyStore.findByKey(key))
            .thenReturn(Optional.of(record(null, OffsetDateTime.now().plusHours(1))));

        assertThatThrownBy(() -> useCase.execute(new CreateCategoryCommand("Periféricos"), key))
            .isInstanceOf(DuplicateRequestInProgressException.class);

        verify(categoryRepository, never()).save(any());
        verify(idempotencyKeyStore, never()).markResolved(any(), any(), anyInt());
    }

    private static Category category(UUID id) {
        OffsetDateTime now = OffsetDateTime.now();
        return new Category(id, "Periféricos", now, now, 0L);
    }

    private static IdempotencyKeyRecord record(UUID resourceId, OffsetDateTime expiresAt) {
        Integer status = resourceId == null ? null : 201;
        return new IdempotencyKeyRecord(UUID.randomUUID(), "POST", "CATEGORY", resourceId, status,
            expiresAt.minusHours(24), expiresAt);
    }
}
