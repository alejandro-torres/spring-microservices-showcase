package com.showcase.order.infrastructure.rest.controller;

import static com.showcase.order.testdata.OrderMother.order;
import static com.showcase.order.testdata.OrderMother.pending;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.ExternalServiceException;
import com.showcase.common.domain.filter.FilterOperator;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.domain.page.SortDirection;
import com.showcase.common.infrastructure.config.CommonWebAutoConfiguration;
import com.showcase.order.application.service.OrderLine;
import com.showcase.order.application.service.OrderService;
import com.showcase.order.domain.model.OrderStatus;
import com.showcase.order.infrastructure.rest.mapper.OrderRestMapperImpl;
import com.showcase.order.infrastructure.security.SecurityConfig;
import com.showcase.order.testdata.TestJwt;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
@Import({CommonWebAutoConfiguration.class, SecurityConfig.class, OrderRestMapperImpl.class})
class OrderControllerTest {

    private static final String CREATE_BODY = """
            {"customerId":3,"items":[{"productId":10,"quantity":2},{"productId":11,"quantity":1}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Nested
    @DisplayName("POST /orders")
    class Create {

        @Test
        @DisplayName("devuelve 201 con el total y las líneas calculadas")
        void create_shouldReturn201() throws Exception {
            given(orderService.create(3L, List.of(new OrderLine(10L, 2), new OrderLine(11L, 1)))).willReturn(pending());

            mockMvc.perform(post("/api/v1/orders").with(TestJwt.manager())
                            .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/v1/orders/1")))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.total").value(30.0))
                    .andExpect(jsonPath("$.items[0].lineTotal").value(10.0))
                    .andExpect(jsonPath("$.shippingAddress.city").value("Madrid"));
        }

        @Test
        @DisplayName("devuelve 400 con una cantidad no válida o sin líneas")
        void create_withInvalidBody_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/orders").with(TestJwt.manager()).contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_BODY.replace("\"quantity\":2", "\"quantity\":0")))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post("/api/v1/orders").with(TestJwt.manager()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerId\":3,\"items\":[]}"))
                    .andExpect(status().isBadRequest());
            verify(orderService, never()).create(any(), any());
        }

        @Test
        @DisplayName("devuelve 503 si otro servicio no está disponible")
        void create_withServiceDown_shouldReturn503() throws Exception {
            given(orderService.create(eq(3L), anyList()))
                    .willThrow(new ExternalServiceException(CommonErrorCode.SERVICE_UNAVAILABLE));

            mockMvc.perform(post("/api/v1/orders").with(TestJwt.manager())
                            .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
        }

        @Test
        @DisplayName("devuelve 403 sin el permiso order:write")
        void create_withoutPermission_shouldReturn403() throws Exception {
            mockMvc.perform(post("/api/v1/orders").with(TestJwt.reader())
                            .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /orders")
    class Read {

        @Test
        @DisplayName("lista con filtros por estado")
        void findAll_withStatusFilter_shouldReturnPage() throws Exception {
            given(orderService.findAll(anyList(), any(), eq(false))).willReturn(PageResult.of(List.of(pending()), 0, 10, 1));

            mockMvc.perform(get("/api/v1/orders").with(TestJwt.reader()).param("filter", "status:in:PENDING,CONFIRMED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("by-customer añade el filtro del cliente y ordena del más reciente al más antiguo")
        void findByCustomer_shouldFilterByCustomer() throws Exception {
            given(orderService.findAll(anyList(), any(), eq(false))).willReturn(PageResult.of(List.of(), 0, 10, 0));

            mockMvc.perform(get("/api/v1/orders/by-customer/3").with(TestJwt.reader()))
                    .andExpect(status().isOk());

            verify(orderService).findAll(
                    argThat(filters -> filters.stream().anyMatch(filter -> filter.field().equals("customerId")
                            && filter.operator() == FilterOperator.EQ && filter.values().contains(3L))),
                    argThat(page -> page.sort().equals("createdAt") && page.direction() == SortDirection.DESC),
                    eq(false));
        }

        @Test
        @DisplayName("devuelve 400 con un valor de estado que no existe")
        void findAll_withUnknownStatus_shouldReturn400() throws Exception {
            mockMvc.perform(get("/api/v1/orders").with(TestJwt.reader()).param("filter", "status:eq:ENVIADO"))
                    .andExpect(status().isBadRequest());
            verify(orderService, never()).findAll(anyList(), any(), anyBoolean());
        }
    }

    @Nested
    @DisplayName("PUT y DELETE")
    class Write {

        @Test
        @DisplayName("PUT sustituye las líneas con la versión enviada")
        void update_shouldReturn200() throws Exception {
            given(orderService.update(1L, List.of(new OrderLine(12L, 1)), 0L)).willReturn(pending());

            mockMvc.perform(put("/api/v1/orders/1").with(TestJwt.manager()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"items\":[{\"productId\":12,\"quantity\":1}],\"version\":0}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PUT /cancel devuelve el pedido cancelado")
        void cancel_shouldReturnCancelled() throws Exception {
            given(orderService.cancel(1L)).willReturn(order(OrderStatus.CANCELLED));

            mockMvc.perform(put("/api/v1/orders/1/cancel").with(TestJwt.manager()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("DELETE es solo para ADMIN")
        void delete_shouldRequireAdmin() throws Exception {
            mockMvc.perform(delete("/api/v1/orders/1").with(TestJwt.manager()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/v1/orders/1").with(TestJwt.admin()))
                    .andExpect(status().isNoContent());
            verify(orderService).delete(1L);
        }
    }
}
