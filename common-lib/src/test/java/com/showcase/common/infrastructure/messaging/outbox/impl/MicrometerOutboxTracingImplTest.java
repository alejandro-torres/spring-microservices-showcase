package com.showcase.common.infrastructure.messaging.outbox.impl;

import static org.assertj.core.api.Assertions.assertThat;

import brave.Tracing;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BravePropagator;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import io.micrometer.tracing.brave.bridge.W3CPropagation;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MicrometerOutboxTracingImplTest {

    private final List<MutableSpan> finishedSpans = new ArrayList<>();

    private Tracing tracing;
    private Tracer tracer;
    private MicrometerOutboxTracingImpl outboxTracing;

    @BeforeEach
    void setUp() {
        tracing = Tracing.newBuilder()
                .currentTraceContext(ThreadLocalCurrentTraceContext.create())
                .supportsJoin(false)
                .addSpanHandler(new SpanHandler() {
                    @Override
                    public boolean end(TraceContext context, MutableSpan span, Cause cause) {
                        finishedSpans.add(span);
                        return true;
                    }
                })
                // The same W3C propagation (traceparent) that Spring Boot configures by default.
                .propagationFactory(new W3CPropagation())
                .build();
        tracer = new BraveTracer(tracing.tracer(), new BraveCurrentTraceContext(tracing.currentTraceContext()),
                new BraveBaggageManager());
        outboxTracing = new MicrometerOutboxTracingImpl(tracer, new BravePropagator(tracing));
    }

    @AfterEach
    void tearDown() {
        tracing.close();
    }

    @Nested
    @DisplayName("currentTraceParent")
    class CurrentTraceParent {

        @Test
        @DisplayName("with no trace in progress it returns null")
        void currentTraceParent_withoutTrace_shouldReturnNull() {
            assertThat(outboxTracing.currentTraceParent()).isNull();
        }

        @Test
        @DisplayName("with a trace in progress it returns its context in W3C traceparent format")
        void currentTraceParent_withTrace_shouldReturnW3cHeader() {
            // given
            Span span = tracer.nextSpan().name("POST /orders").start();

            // when
            String traceParent;
            try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                traceParent = outboxTracing.currentTraceParent();
            } finally {
                span.end();
            }

            // then
            assertThat(traceParent).isEqualTo("00-" + span.context().traceId() + "-" + span.context().spanId()
                    + "-01");
        }
    }

    @Nested
    @DisplayName("callWithParent")
    class CallWithParent {

        @Test
        @DisplayName("runs the action as a child of the stored trace")
        void callWithParent_shouldContinueStoredTrace() {
            // given
            String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
            String parentSpanId = "00f067aa0ba902b7";

            // when
            String seenTraceId = outboxTracing.callWithParent("00-" + traceId + "-" + parentSpanId + "-01",
                    "outbox order.created", () -> tracer.currentTraceContext().context().traceId());

            // then
            assertThat(seenTraceId).isEqualTo(traceId);
            assertThat(finishedSpans).singleElement().satisfies(span -> {
                assertThat(span.name()).isEqualTo("outbox order.created");
                assertThat(span.traceId()).isEqualTo(traceId);
                assertThat(span.parentId()).isEqualTo(parentSpanId);
            });
        }

        @Test
        @DisplayName("with no stored trace it runs the action without creating spans")
        void callWithParent_withoutTraceParent_shouldJustRun() {
            // when
            String result = outboxTracing.callWithParent(null, "outbox order.created", () -> "sent");

            // then
            assertThat(result).isEqualTo("sent");
            assertThat(finishedSpans).isEmpty();
        }
    }
}
