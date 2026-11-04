package com.showcase.customer.infrastructure.rest.controller;

import static com.showcase.customer.testdata.CustomerMother.ana;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
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

import com.showcase.common.domain.exception.ResourceNotFoundException;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.infrastructure.config.CommonWebAutoConfiguration;
import com.showcase.customer.application.service.CustomerService;
import com.showcase.customer.domain.exception.CustomerErrorCode;
import com.showcase.customer.infrastructure.rest.mapper.CustomerRestMapperImpl;
import com.showcase.customer.infrastructure.security.SecurityConfig;
import com.showcase.customer.testdata.TestJwt;
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

@WebMvcTest(CustomerController.class)
@Import({CommonWebAutoConfiguration.class, SecurityConfig.class, CustomerRestMapperImpl.class})
class CustomerControllerTest {

    private static final String CREATE_BODY = """
            {"userId":7,"firstName":"Ana","lastName":"García","email":"ana@example.com",
             "addresses":[{"street":"Main Street 1","city":"Madrid","postalCode":"28013","country":"ES"}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Nested
    @DisplayName("POST /customers")
    class Create {

        @Test
        @DisplayName("returns 201 with the location and the addresses")
        void create_shouldReturn201() throws Exception {
            given(customerService.create(eq(7L), eq("Ana"), eq("García"), eq("ana@example.com"), eq(null), anyList()))
                    .willReturn(ana());

            mockMvc.perform(post("/api/v1/customers").with(TestJwt.manager())
                            .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/v1/customers/1")))
                    .andExpect(jsonPath("$.addresses[0].main").value(true))
                    .andExpect(jsonPath("$.addresses.length()").value(2));
        }

        @Test
        @DisplayName("returns 400 if the email is invalid and does not call the service")
        void create_withInvalidEmail_shouldReturn400() throws Exception {
            mockMvc.perform(post("/api/v1/customers").with(TestJwt.manager())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_BODY.replace("ana@example.com", "not-an-email")))
                    .andExpect(status().isBadRequest());
            verify(customerService, never()).create(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("returns 403 without the customer:write permission")
        void create_withoutWritePermission_shouldReturn403() throws Exception {
            mockMvc.perform(post("/api/v1/customers").with(TestJwt.reader())
                            .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("returns 401 without a token")
        void create_withoutToken_shouldReturn401() throws Exception {
            mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /customers")
    class Read {

        @Test
        @DisplayName("returns 404 with the message in Spanish if requested")
        void findById_missing_shouldReturn404() throws Exception {
            given(customerService.findById(9L))
                    .willThrow(new ResourceNotFoundException(CustomerErrorCode.CUSTOMER_NOT_FOUND, 9L));

            mockMvc.perform(get("/api/v1/customers/9").with(TestJwt.reader())
                            .header(HttpHeaders.ACCEPT_LANGUAGE, "es"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("No existe ningún cliente activo con id 9."));
        }

        @Test
        @DisplayName("looks up by auth-service user")
        void findByUserId_shouldReturnCustomer() throws Exception {
            given(customerService.findByUserId(7L)).willReturn(ana());

            mockMvc.perform(get("/api/v1/customers/by-user/7").with(TestJwt.reader()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("ana@example.com"));
        }

        @Test
        @DisplayName("lists with whitelisted filters")
        void findAll_withFilter_shouldReturnPage() throws Exception {
            given(customerService.findAll(anyList(), any(), eq(false)))
                    .willReturn(PageResult.of(List.of(ana()), 0, 10, 1));

            mockMvc.perform(get("/api/v1/customers").with(TestJwt.reader()).param("filter", "city:eq:Madrid"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("returns 400 with a filter field that is not allowed")
        void findAll_withUnknownFilter_shouldReturn400() throws Exception {
            mockMvc.perform(get("/api/v1/customers").with(TestJwt.reader()).param("filter", "phone:eq:600"))
                    .andExpect(status().isBadRequest());
            verify(customerService, never()).findAll(anyList(), any(), anyBoolean());
        }

        @Test
        @DisplayName("only ADMIN can include inactive customers")
        void findAll_includeInactiveWithoutAdmin_shouldReturn403() throws Exception {
            mockMvc.perform(get("/api/v1/customers").with(TestJwt.manager()).param("includeInactive", "true"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PUT, DELETE and addresses")
    class Write {

        @Test
        @DisplayName("PUT updates with the version sent")
        void update_shouldReturn200() throws Exception {
            given(customerService.update(1L, "Ana", "García", "ana@example.com", null, 0L)).willReturn(ana());

            mockMvc.perform(put("/api/v1/customers/1").with(TestJwt.manager())
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"firstName":"Ana","lastName":"García","email":"ana@example.com","version":0}
                                    """))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("DELETE devuelve 204")
        void delete_shouldReturn204() throws Exception {
            mockMvc.perform(delete("/api/v1/customers/1").with(TestJwt.manager()))
                    .andExpect(status().isNoContent());
            verify(customerService).delete(1L);
        }

        @Test
        @DisplayName("Address POST returns the updated customer")
        void addAddress_shouldReturnCustomer() throws Exception {
            given(customerService.addAddress(eq(1L), any())).willReturn(ana());

            mockMvc.perform(post("/api/v1/customers/1/addresses").with(TestJwt.manager())
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"street":"High Street 3","city":"Sevilla","postalCode":"41001","country":"ES"}
                                    """))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("DELETE and PUT main of an address delegate to the service")
        void removeAndSetMain_shouldDelegate() throws Exception {
            given(customerService.removeAddress(1L, 11L)).willReturn(ana());
            given(customerService.setMainAddress(1L, 10L)).willReturn(ana());

            mockMvc.perform(delete("/api/v1/customers/1/addresses/11").with(TestJwt.manager()))
                    .andExpect(status().isOk());
            mockMvc.perform(put("/api/v1/customers/1/addresses/10/main").with(TestJwt.manager()))
                    .andExpect(status().isOk());
        }
    }
}
