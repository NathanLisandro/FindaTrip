package com.smarttravel.analyzer.presentation.handler;

import com.smarttravel.analyzer.domain.exception.DomainException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException ex) { var problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY); problem.setTitle("Business rule violation"); problem.setDetail(ex.getMessage()); return problem; }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) { var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST); problem.setTitle("Invalid request"); problem.setDetail("Request fields failed validation"); return problem; }
}
