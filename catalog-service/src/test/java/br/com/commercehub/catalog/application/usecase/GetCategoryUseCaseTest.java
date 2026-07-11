package br.com.commercehub.catalog.application.usecase;

import br.com.commercehub.catalog.application.port.CategoryRepository;
import br.com.commercehub.catalog.domain.exception.CategoryNotFoundException;
import br.com.commercehub.catalog.domain.model.Category;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCategoryUseCaseTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private GetCategoryUseCase useCase;

    @Test
    void existingCategoryIsReturned() {
        Category category = category();
        when(categoryRepository.findById(category.id())).thenReturn(Optional.of(category));

        assertThat(useCase.execute(category.id())).isEqualTo(category);
    }

    @Test
    void unknownCategoryThrowsCategoryNotFoundException() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id))
            .isInstanceOf(CategoryNotFoundException.class);
    }

    private static Category category() {
        OffsetDateTime now = OffsetDateTime.now();
        return new Category(UUID.randomUUID(), "Periféricos", now, now, 0L);
    }
}
