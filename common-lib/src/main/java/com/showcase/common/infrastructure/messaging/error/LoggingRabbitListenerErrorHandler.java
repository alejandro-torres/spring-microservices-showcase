package com.showcase.common.infrastructure.messaging.error;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.rabbit.listener.api.RabbitListenerErrorHandler;

/**
 * Listener exceptions do not go through the {@code GlobalExceptionHandler} (there is no HTTP request). This
 * handler, referenced in each {@code @RabbitListener(errorHandler = ...)}, records the failure with the
 * {@code eventId}, the queue and whether it will be retried (the {@code traceId} is added by the log's MDC), and
 * rethrows the exception: the retry policy decides with {@link MessagingExceptionClassifier} whether to retry or
 * send the message straight to the DLQ.
 */
public class LoggingRabbitListenerErrorHandler implements RabbitListenerErrorHandler {

    public static final String BEAN_NAME = "messagingErrorHandler";

    private static final Logger log = LoggerFactory.getLogger(LoggingRabbitListenerErrorHandler.class);

    @Override
    public Object handleError(Message amqpMessage, Channel channel, org.springframework.messaging.Message<?> message,
                              ListenerExecutionFailedException exception) {
        MessageProperties properties = amqpMessage.getMessageProperties();
        Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
        if (MessagingExceptionClassifier.isRetryable(exception)) {
            log.warn("Error processing event {} ({}) from queue {}; it will be retried: {}",
                    properties.getMessageId(), properties.getType(), properties.getConsumerQueue(),
                    cause.toString());
        } else {
            log.error("Event {} ({}) from queue {} rejected without retries, going to the DLQ: {}",
                    properties.getMessageId(), properties.getType(), properties.getConsumerQueue(),
                    cause.toString());
        }
        throw exception;
    }
}
