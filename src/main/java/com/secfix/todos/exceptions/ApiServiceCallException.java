package com.secfix.todos.exceptions;

import lombok.Data;
import org.springframework.http.HttpStatus;


@Data
public class ApiServiceCallException extends RuntimeException {
    private String message;
    private HttpStatus httpStatus;

     public ApiServiceCallException(String message, HttpStatus httpStatus) {
        super(message);
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
