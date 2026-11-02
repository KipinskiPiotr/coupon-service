package com.empik.couponservice.error;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Renders all errors as RFC 9457 Problem Details. Business errors carry an additional {@code code}
 * property, so clients can distinguish errors that share the same HTTP status.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final String CODE_PROPERTY = "code";

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
		ErrorCode errorCode = ex.errorCode();
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.status(), ex.getMessage());
		problem.setProperty(CODE_PROPERTY, errorCode.name());
		return ResponseEntity.of(problem).build();
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldError> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
			.toList();

		ProblemDetail problem = ex.getBody();
		problem.setDetail("Request validation failed");
		problem.setProperty(CODE_PROPERTY, ErrorCode.VALIDATION_FAILED.name());
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	record FieldError(String field, String message) {
	}

}
