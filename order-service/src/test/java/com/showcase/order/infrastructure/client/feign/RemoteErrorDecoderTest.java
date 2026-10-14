package com.showcase.order.infrastructure.client.feign;

import static org.assertj.core.api.Assertions.assertThat;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.ExternalServiceException;
import com.showcase.common.domain.exception.ForbiddenException;
import com.showcase.common.domain.exception.UnauthorizedException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RemoteErrorDecoderTest {

    private final RemoteErrorDecoder decoder = new RemoteErrorDecoder();

    private Exception decode(int status) {
        var request = Request.create(Request.HttpMethod.GET, "http://product-service/api/v1/products/1", Map.of(),
                null, StandardCharsets.UTF_8, null);
        var response = Response.builder().status(status).request(request).headers(Map.of())
                .body("{\"detalle\":\"interno\"}", StandardCharsets.UTF_8).build();
        return decoder.decode("ProductFeignClient#findById(Long)", response);
    }

    @Nested
    @DisplayName("decode")
    class Decode {

        @ParameterizedTest
        @ValueSource(ints = 404)
        @DisplayName("404 es un recurso que no existe")
        void decode_404_shouldBeNotFound(int status) {
            assertThat(decode(status)).isInstanceOf(RemoteResourceNotFoundException.class);
        }

        @ParameterizedTest
        @ValueSource(ints = 401)
        @DisplayName("401 es un token no válido")
        void decode_401_shouldBeUnauthorized(int status) {
            assertThat(decode(status)).isInstanceOf(UnauthorizedException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.INVALID_TOKEN);
        }

        @ParameterizedTest
        @ValueSource(ints = 403)
        @DisplayName("403 es falta de permisos del usuario")
        void decode_403_shouldBeForbidden(int status) {
            assertThat(decode(status)).isInstanceOf(ForbiddenException.class);
        }

        @ParameterizedTest
        @ValueSource(ints = {400, 409, 422, 500, 502, 503})
        @DisplayName("el resto es un servicio no disponible, sin reenviar el cuerpo remoto")
        void decode_other_shouldBeServiceUnavailable(int status) {
            assertThat(decode(status)).isInstanceOf(ExternalServiceException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.SERVICE_UNAVAILABLE)
                    .hasMessageNotContaining("interno");
        }
    }
}
