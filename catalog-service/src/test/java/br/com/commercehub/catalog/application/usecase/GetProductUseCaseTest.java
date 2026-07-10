package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.ProductRepository;
import br.com.commercehub.catalog.domain.exception.ProductNotFoundException;
import br.com.commercehub.catalog.domain.model.Product;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetProductUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private GetProductUseCase useCase;

    @Test
    void activeProductIsReturned() {
        Product active = product(true);
        when(productRepository.findById(active.id())).thenReturn(Optional.of(active));

        assertThat(useCase.execute(active.id())).isEqualTo(active);
    }

    /** Produto soft-deleted precisa ser indistinguível de inexistente: 404 nos dois casos. */
    @Test
    void inactiveProductThrowsProductNotFoundException() {
        Product inactive = product(false);
        when(productRepository.findById(inactive.id())).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> useCase.execute(inactive.id()))
            .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void unknownProductThrowsProductNotFoundException() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id))
            .isInstanceOf(ProductNotFoundException.class);
    }

    private static Product product(boolean active) {
        OffsetDateTime now = OffsetDateTime.now();
        return new Product(UUID.randomUUID(), "Teclado", "Mecânico", new BigDecimal("199.90"),
            UUID.randomUUID(), active, now, now, 0L);
    }
}
