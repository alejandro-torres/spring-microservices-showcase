package com.showcase.common.infrastructure.exception;

import com.showcase.common.domain.exception.BusinessRuleException;
import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.ErrorCategory;
import com.showcase.common.domain.exception.ErrorCode;
import com.showcase.common.domain.exception.ExternalServiceException;
import com.showcase.common.domain.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API de prueba que lanza cada tipo de excepción que debe traducir el GlobalExceptionHandler. */
@RestController
@RequestMapping("/test")
class ErrorTestController {

    enum TestErrorCode implements ErrorCode {
        INSUFFICIENT_STOCK;

        @Override
        public String code() {
            return name();
        }

        @Override
        public ErrorCategory category() {
            return ErrorCategory.BUSINESS_RULE;
        }
    }

    record TestRequestDTO(@NotBlank String name, @Size(min = 3, max = 10) String code, @Positive Integer quantity) {
    }

    @GetMapping("/not-found")
    String notFound() {
        throw new ResourceNotFoundException(CommonErrorCode.RESOURCE_NOT_FOUND, "Product", 25000L);
    }

    @GetMapping("/business-rule")
    String businessRule() {
        throw new BusinessRuleException(TestErrorCode.INSUFFICIENT_STOCK, 7);
    }

    @GetMapping("/external")
    String external() {
        throw new ExternalServiceException(CommonErrorCode.SERVICE_UNAVAILABLE, new IllegalStateException("timeout"));
    }

    @PostMapping("/body")
    String body(@Valid @RequestBody TestRequestDTO request) {
        return "ok";
    }

    @GetMapping("/param")
    String param(@RequestParam @Max(100) int size) {
        return "ok";
    }

    @GetMapping("/typed/{id}")
    String typed(@PathVariable Long id) {
        return "ok";
    }

    @GetMapping("/required")
    String required(@RequestParam String q) {
        return q;
    }

    @GetMapping("/unexpected")
    String unexpected() {
        throw new NullPointerException("detalle interno secreto");
    }

    @GetMapping("/optimistic-lock")
    String optimisticLock() {
        throw new OptimisticLockingFailureException("Row was updated or deleted by another transaction");
    }

    @GetMapping("/data-integrity")
    String dataIntegrity() {
        throw new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"products_name_key\"");
    }

    @GetMapping("/constraint-violation")
    String constraintViolation() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new TestRequestDTO("", "ab", -1));
            throw new ConstraintViolationException(violations);
        }
    }
}
