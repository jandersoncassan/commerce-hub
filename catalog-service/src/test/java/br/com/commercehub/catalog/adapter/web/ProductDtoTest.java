package br.com.commercehub.catalog.adapter.web;

import br.com.commercehub.catalog.domain.model.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductDtoTest {

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
        var request = new ProductCreateRequest("  ", "descrição", BigDecimal.TEN, UUID.randomUUID());

        Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);

        assertThat(violations)
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains("name");
    }

    @Test
    void createRequestWithAllFieldsPresentPassesValidation() {
        var request = new ProductCreateRequest("Produto", "descrição", BigDecimal.TEN, UUID.randomUUID());

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void createRequestWithMissingRequiredFieldsFailsValidation() {
        var request = new ProductCreateRequest(null, null, null, null);

        Set<ConstraintViolation<ProductCreateRequest>> violations = validator.validate(request);

        assertThat(violations)
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains("name", "price", "categoryId");
    }

    @Test
    void createRequestWithMalformedPriceFieldFailsJsonDeserialization() {
        var objectMapper = new ObjectMapper();
        var json = """
            {"name":"Produto","description":"descrição","price":"não é um número","categoryId":"%s"}
            """.formatted(UUID.randomUUID());

        assertThrows(InvalidFormatException.class, () -> objectMapper.readValue(json, ProductCreateRequest.class));
    }

    @Test
    void updateRequestWithBlankNameOrMissingVersionFailsValidation() {
        var request = new ProductUpdateRequest("", "descrição", BigDecimal.ONE, UUID.randomUUID(), null);

        Set<ConstraintViolation<ProductUpdateRequest>> violations = validator.validate(request);

        assertThat(violations)
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains("name", "version");
    }

    @Test
    void productResponseFromDomainCopiesAllFields() {
        var now = OffsetDateTime.now();
        var categoryId = UUID.randomUUID();
        var product = new Product(UUID.randomUUID(), "Produto", "descrição", BigDecimal.TEN, categoryId,
            true, now, now, 3L);

        var response = ProductResponse.from(product);

        assertThat(response.id()).isEqualTo(product.id());
        assertThat(response.name()).isEqualTo(product.name());
        assertThat(response.description()).isEqualTo(product.description());
        assertThat(response.price()).isEqualTo(product.price());
        assertThat(response.categoryId()).isEqualTo(categoryId);
        assertThat(response.active()).isTrue();
        assertThat(response.createdAt()).isEqualTo(now);
        assertThat(response.updatedAt()).isEqualTo(now);
        assertThat(response.version()).isEqualTo(3L);
    }
}
