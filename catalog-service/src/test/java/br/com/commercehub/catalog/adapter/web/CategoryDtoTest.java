package br.com.commercehub.catalog.adapter.web;

import br.com.commercehub.catalog.domain.model.Category;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CategoryDtoTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void createRequestWithBlankNameFailsValidation() {
        var request = new CategoryCreateRequest("  ");

        Set<ConstraintViolation<CategoryCreateRequest>> violations = validator.validate(request);

        assertThat(violations)
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains("name");
    }

    @Test
    void createRequestWithNamePresentPassesValidation() {
        var request = new CategoryCreateRequest("Eletrônicos");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void createRequestWithMalformedNameFieldFailsJsonDeserialization() {
        var objectMapper = new ObjectMapper();
        var json = """
            {"name":{"nested":"objeto em vez de string"}}
            """;

        assertThrows(MismatchedInputException.class, () -> objectMapper.readValue(json, CategoryCreateRequest.class));
    }

    @Test
    void updateRequestWithBlankNameOrMissingVersionFailsValidation() {
        var request = new CategoryUpdateRequest("", null);

        Set<ConstraintViolation<CategoryUpdateRequest>> violations = validator.validate(request);

        assertThat(violations)
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains("name", "version");
    }

    @Test
    void categoryResponseFromDomainCopiesAllFields() {
        var now = OffsetDateTime.now();
        var category = new Category(UUID.randomUUID(), "Eletrônicos", now, now, 2L);

        var response = CategoryResponse.from(category);

        assertThat(response.id()).isEqualTo(category.id());
        assertThat(response.name()).isEqualTo(category.name());
        assertThat(response.createdAt()).isEqualTo(now);
        assertThat(response.updatedAt()).isEqualTo(now);
        assertThat(response.version()).isEqualTo(2L);
    }
}
