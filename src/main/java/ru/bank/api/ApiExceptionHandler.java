package ru.bank.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.bank.service.BusinessException;
import javax.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String,Object>> business(BusinessException e) { return error(e.status, e.code, e.getMessage()); }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
        HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, ServletRequestBindingException.class})
    public ResponseEntity<Map<String,Object>> validation(Exception e) { return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request parameters or body"); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String,Object>> conflict(DataIntegrityViolationException e) {
        return error(HttpStatus.CONFLICT, "DATA_CONFLICT", "Contact is already used or a data constraint would be violated");
    }
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<Map<String,Object>> busy(ConcurrencyFailureException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "RETRY_LATER", "Concurrent operation timed out; retry with the same idempotency key");
    }
    private ResponseEntity<Map<String,Object>> error(HttpStatus status, String code, String message) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("code", code); body.put("message", message); body.put("requestId", MDC.get("requestId"));
        if (status.is5xxServerError()) { log.warn("Request failed code={}", code); }
        return ResponseEntity.status(status).body(body);
    }
}
