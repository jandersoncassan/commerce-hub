package br.com.commercehub.catalog.adapter.web;

import java.time.OffsetDateTime;

public record ErrorResponse(int status, String message, OffsetDateTime timestamp) {

    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message, OffsetDateTime.now());
    }
}
