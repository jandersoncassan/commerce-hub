package br.com.commercehub.catalog.adapter.web;

import br.com.commercehub.catalog.domain.exception.CategoryHasActiveProductsException;
import br.com.commercehub.catalog.domain.exception.CategoryNotFoundException;
import br.com.commercehub.catalog.domain.exception.DuplicateRequestInProgressException;
import br.com.commercehub.catalog.domain.exception.InvalidCategoryException;
import br.com.commercehub.catalog.domain.exception.InvalidPriceException;
import br.com.commercehub.catalog.domain.exception.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Mapeamento único de exceção para status HTTP (seção 9 do plan.md), para todos os
 * controllers. O corpo de erro ({@link ErrorResponse}) não é um requisito do specify.md — é
 * uma escolha de implementação, igual para todos os casos.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        return responseFor(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler({ProductNotFoundException.class, CategoryNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex) {
        return responseFor(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DuplicateRequestInProgressException.class})
    public ResponseEntity<ErrorResponse> handleConflict(Exception ex) {
        return responseFor(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({
        InvalidPriceException.class,
        InvalidCategoryException.class,
        CategoryHasActiveProductsException.class
    })
    public ResponseEntity<ErrorResponse> handleUnprocessableEntity(RuntimeException ex) {
        return responseFor(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    private static ResponseEntity<ErrorResponse> responseFor(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status.value(), message));
    }
}
