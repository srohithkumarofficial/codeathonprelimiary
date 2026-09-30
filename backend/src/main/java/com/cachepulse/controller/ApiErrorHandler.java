package com.cachepulse.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "Invalid request" : exception.getMessage();
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<Map<String, String>> malformedRequest(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid or incomplete request"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> internalError(Exception exception) {
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "Request could not be completed"));
    }
}
