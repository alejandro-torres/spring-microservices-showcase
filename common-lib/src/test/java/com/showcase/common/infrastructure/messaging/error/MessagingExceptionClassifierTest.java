package com.showcase.common.infrastructure.messaging.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.showcase.common.domain.exception.BusinessRuleException;
import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.ConflictException;
import com.showcase.common.domain.exception.ExternalServiceException;
import com.showcase.common.domain.exception.InvalidRequestException;
import com.showcase.common.domain.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;

class MessagingExceptionClassifierTest {

    @Nested
    @DisplayName("isRetryable")
    class IsRetryable {

        @Test
        @DisplayName("does not retry a malformed message")
        void isRetryable_withConversionError_shouldReturnFalse() {
            // given
            var error = wrap(new MessageConversionException("Invalid JSON"));

            // when / then
            assertThat(MessagingExceptionClassifier.isRetryable(error)).isFalse();
        }

        @Test
        @DisplayName("does not retry business errors: invalid data, missing resource, rule or conflict")
        void isRetryable_withBusinessErrors_shouldReturnFalse() {
            assertThat(MessagingExceptionClassifier.isRetryable(
                    wrap(new InvalidRequestException(CommonErrorCode.VALIDATION_ERROR)))).isFalse();
            assertThat(MessagingExceptionClassifier.isRetryable(
                    wrap(new ResourceNotFoundException(CommonErrorCode.RESOURCE_NOT_FOUND, "order", 1)))).isFalse();
            assertThat(MessagingExceptionClassifier.isRetryable(
                    wrap(new BusinessRuleException(CommonErrorCode.VALIDATION_ERROR)))).isFalse();
            assertThat(MessagingExceptionClassifier.isRetryable(
                    wrap(new ConflictException(CommonErrorCode.DATA_CONFLICT)))).isFalse();
        }

        @Test
        @DisplayName("retries transient errors: another service down, locks and concurrency conflicts")
        void isRetryable_withTransientErrors_shouldReturnTrue() {
            assertThat(MessagingExceptionClassifier.isRetryable(
                    wrap(new ExternalServiceException(CommonErrorCode.SERVICE_UNAVAILABLE)))).isTrue();
            assertThat(MessagingExceptionClassifier.isRetryable(
                    wrap(new OptimisticLockingFailureException("version")))).isTrue();
            assertThat(MessagingExceptionClassifier.isRetryable(
                    wrap(new CannotAcquireLockException("lock")))).isTrue();
        }

        @Test
        @DisplayName("retries an unexpected error")
        void isRetryable_withUnexpectedError_shouldReturnTrue() {
            assertThat(MessagingExceptionClassifier.isRetryable(wrap(new IllegalStateException("boom")))).isTrue();
        }
    }

    private static ListenerExecutionFailedException wrap(Throwable cause) {
        return new ListenerExecutionFailedException("listener failed", cause);
    }
}
