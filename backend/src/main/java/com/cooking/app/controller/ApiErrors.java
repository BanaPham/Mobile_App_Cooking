package com.cooking.app.controller;

import com.cooking.app.dto.AuthDtos.Message;
import com.cooking.app.service.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Message> apiError(ApiException error) {
        return ResponseEntity.status(error.status).body(new Message(error.getMessage()));
    }
}
