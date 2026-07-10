package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.ProductRepository;
import br.com.commercehub.catalog.domain.exception.ProductNotFoundException;
import br.com.commercehub.catalog.domain.model.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeactivateProductUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private DeactivateProductUseCase useCase;

    @Captor
    private ArgumentCaptor<Product> savedProduct;

    @Test
    void activeProductIsDeactivated() {
        Product active = product(true);
        when(productRepository.findById(active.id())).thenReturn(Optional.of(active));

        useCase.execute(active.id());

        verify(productRepository).save(savedProduct.capture());
        Product saved = savedProduct.getValue();
        assertThat(saved.active()).isFalse();
        assertThat(saved.updatedAt()).isAfter(active.updatedAt());
        assertThat(saved.id()).isEqualTo(active.id());
        assertThat(saved.name()).isEqualTo(active.name());
        assertThat(saved.price()).isEqualByComparingTo(active.price());
        assertThat(saved.createdAt()).isEqualTo(active.createdAt());
    }

    /**
     * Nenhum {@code save} significa nenhum UPDATE — é o que prova, e não apenas aproxima,
     * que {@code updatedAt} permanece intacto para um produto já inativo.
     */
    @Test
    void alreadyInactiveProductDoesNotThrowAndIsNotSavedAgain() {
        Product inactive = product(false);
        when(productRepository.findById(inactive.id())).thenReturn(Optional.of(inactive));

        assertThatCode(() -> useCase.execute(inactive.id())).doesNotThrowAnyException();

        verify(productRepository, never()).save(any());
    }

    @Test
    void unknownProductThrowsProductNotFoundException() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id))
            .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).save(any());
    }

    private static Product product(boolean active) {
        OffsetDateTime createdAt = OffsetDateTime.now().minusDays(1);
        return new Product(UUID.randomUUID(), "Teclado", "Mecânico", new BigDecimal("199.90"),
            UUID.randomUUID(), active, createdAt, createdAt, 3L);
    }
}
