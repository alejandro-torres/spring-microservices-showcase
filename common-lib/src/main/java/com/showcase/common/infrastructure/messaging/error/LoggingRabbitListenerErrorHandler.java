package com.showcase.common.infrastructure.messaging.error;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.rabbit.listener.api.RabbitListenerErrorHandler;

/**
 * Las excepciones de los listeners no pasan por el {@code GlobalExceptionHandler} (no hay petición HTTP). Este
 * handler, referenciado en cada {@code @RabbitListener(errorHandler = ...)}, registra el fallo con el
 * {@code eventId}, la cola y si se va a reintentar (el {@code traceId} lo añade el MDC del log), y relanza la
 * excepción: la política de reintentos decide con {@link MessagingExceptionClassifier} si se reintenta o si el
 * mensaje va directo a la DLQ.
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
            log.warn("Error al procesar el evento {} ({}) de la cola {}; se reintentará: {}",
                    properties.getMessageId(), properties.getType(), properties.getConsumerQueue(),
                    cause.toString());
        } else {
            log.error("Evento {} ({}) de la cola {} rechazado sin reintentos, va a la DLQ: {}",
                    properties.getMessageId(), properties.getType(), properties.getConsumerQueue(),
                    cause.toString());
        }
        throw exception;
    }
}
