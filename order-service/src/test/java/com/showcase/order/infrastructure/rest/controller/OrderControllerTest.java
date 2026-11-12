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

import com.showcase.common.domain.exception.BusinessRuleException;
import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.ExternalServiceException;
import com.showcase.common.domain.filter.FilterOperator;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.domain.page.SortDirection;
import com.showcase.common.infrastructure.config.CommonWebAutoConfiguration;
import com.showcase.order.application.service.OrderLine;
import com.showcase.order.application.service.OrderService;
import com.showcase.order.domain.exception.OrderErrorCode;
import com.showcase.order.domain.model.OrderStatus;
import com.showcase.order.domain.model.ShippingAddress;
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
        @DisplayName("returns 201 with the computed total and lines")
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
        @DisplayName("returns 400 with an invalid quantity or no lines")
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
        @DisplayName("returns 503 if another service is unavailable")
        void create_withServiceDown_shouldReturn503() throws Exception {
            given(orderService.create(eq(3L), anyList()))
                    .willThrow(new ExternalServiceException(CommonErrorCode.SERVICE_UNAVAILABLE));

            mockMvc.perform(post("/api/v1/orders").with(TestJwt.manager())
                            .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
        }

        @Test
        @DisplayName("returns 403 without the order:write permission")
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
        @DisplayName("lists with status filters")
        void findAll_withStatusFilter_shouldReturnPage() throws Exception {
            given(orderService.findAll(anyList(), any(), eq(false))).willReturn(PageResult.of(List.of(pending()), 0, 10, 1));

            mockMvc.perform(get("/api/v1/orders").with(TestJwt.reader()).param("filter", "status:in:PENDING,CONFIRMED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("by-customer adds the customer filter and sorts from most recent to oldest")
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
        @DisplayName("returns 400 with a status value that does not exist")
        void findAll_withUnknownStatus_shouldReturn400() throws Exception {
            mockMvc.perform(get("/api/v1/orders").with(TestJwt.reader()).param("filter", "status:eq:ENVIADO"))
                    .andExpect(status().isBadRequest());
            verify(orderService, never()).findAll(anyList(), any(), anyBoolean());
        }
    }

    @Nested
    @DisplayName("PUT and DELETE")
    class Write {

        @Test
        @DisplayName("PUT changes the shipping address with the version sent")
        void update_shouldReturn200() throws Exception {
            var address = new ShippingAddress("High Street 5", "Sevilla", "41001", "ES");
            given(orderService.update(1L, address, 0L)).willReturn(pending());

            mockMvc.perform(put("/api/v1/orders/1").with(TestJwt.manager()).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"street":"High Street 5","city":"Sevilla","postalCode":"41001",
                                     "country":"ES","version":0}"""))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PUT returns 400 with a country that is not 2 uppercase letters or without a version")
        void update_withInvalidBody_shouldReturn400() throws Exception {
            mockMvc.perform(put("/api/v1/orders/1").with(TestJwt.manager()).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"street":"High Street 5","city":"Sevilla","postalCode":"41001",
                                     "country":"spain"}"""))
                    .andExpect(status().isBadRequest());
            verify(orderService, never()).update(any(), any(), any());
        }

        @Test
        @DisplayName("PUT /cancel returns the cancelled order")
        void cancel_shouldReturnCancelled() throws Exception {
            given(orderService.cancel(1L)).willReturn(order(OrderStatus.CANCELLED));

            mockMvc.perform(put("/api/v1/orders/1/cancel").with(TestJwt.manager()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("PUT /cancel of a pending order returns 422 ORDER_NOT_CANCELLABLE")
        void cancel_pending_shouldReturn422() throws Exception {
            given(orderService.cancel(1L)).willThrow(
                    new BusinessRuleException(OrderErrorCode.ORDER_NOT_CANCELLABLE, 1L, OrderStatus.PENDING));

            mockMvc.perform(put("/api/v1/orders/1/cancel").with(TestJwt.manager()))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
        }

        @Test
        @DisplayName("DELETE is ADMIN only")
        void delete_shouldRequireAdmin() throws Exception {
            mockMvc.perform(delete("/api/v1/orders/1").with(TestJwt.manager()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/v1/orders/1").with(TestJwt.admin()))
                    .andExpect(status().isNoContent());
            verify(orderService).delete(1L);
        }
    }
}
