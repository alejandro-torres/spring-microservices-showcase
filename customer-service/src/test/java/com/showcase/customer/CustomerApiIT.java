package com.showcase.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.showcase.customer.testdata.TestJwt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end test against PostgreSQL: address cascade, orphan deletion,
 * email and username uniqueness, optimistic locking and soft delete.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CustomerApiIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(TestJwt.admin()));
    }

    private ResultActions json(MockHttpServletRequestBuilder request, String body) throws Exception {
        return asAdmin(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static long read(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }

    @Test
    @DisplayName("walks through the lifecycle of a customer with their addresses")
    void fullCustomerFlow() throws Exception {
        String create = """
                {"userId":900,"firstName":"Lucía","lastName":"Martín","email":"Lucia.e2e@example.com",
                 "addresses":[{"street":"Main Street 1","city":"Zamora","postalCode":"49001","country":"es"},
                              {"street":"New Street 2","city":"Lugo","postalCode":"27001","country":"ES"}]}
                """;
        var created = json(post("/api/v1/customers"), create).andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("lucia.e2e@example.com"))
                .andExpect(jsonPath("$.addresses[0].main").value(true))
                .andExpect(jsonPath("$.addresses[1].main").value(false));
        long id = read(created, "$.id");
        long first = read(created, "$.addresses[0].id");
        long second = read(created, "$.addresses[1].id");

        // Email (case-insensitive) and username are unique.
        json(post("/api/v1/customers"), create.replace("900", "901"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
        json(post("/api/v1/customers"), create.replace("Lucia.e2e", "other"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_ALREADY_HAS_CUSTOMER"));

        // Filter by city on the relation with addresses.
        asAdmin(get("/api/v1/customers").param("filter", "city:eq:Lugo"))
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")]").exists());
        asAdmin(get("/api/v1/customers/by-user/900")).andExpect(jsonPath("$.id").value(id));

        // Change the primary one and delete the old one: address ids are preserved.
        asAdmin(put("/api/v1/customers/" + id + "/addresses/" + second + "/main")).andExpect(status().isOk());
        asAdmin(delete("/api/v1/customers/" + id + "/addresses/" + first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses.length()").value(1))
                .andExpect(jsonPath("$.addresses[0].id").value(second))
                .andExpect(jsonPath("$.addresses[0].main").value(true));
        var added = json(post("/api/v1/customers/" + id + "/addresses"),
                "{\"street\":\"Real Street 5\",\"city\":\"León\",\"postalCode\":\"24001\",\"country\":\"ES\"}")
                .andExpect(jsonPath("$.addresses.length()").value(2))
                .andExpect(jsonPath("$.addresses[1].main").value(false));

        // Optimistic locking: an old version gives 409.
        long version = read(added, "$.version");
        String update = "{\"firstName\":\"Lucía\",\"lastName\":\"Martín Gil\",\"email\":\"lucia.e2e@example.com\","
                + "\"version\":%d}";
        json(put("/api/v1/customers/" + id), update.formatted(version)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Martín Gil"));
        json(put("/api/v1/customers/" + id), update.formatted(version))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        // Soft delete: can no longer be found, except by ADMIN with includeInactive.
        asAdmin(delete("/api/v1/customers/" + id)).andExpect(status().isNoContent());
        asAdmin(get("/api/v1/customers/" + id)).andExpect(status().isNotFound());
        asAdmin(get("/api/v1/customers").param("includeInactive", "true").param("filter", "userId:eq:900"))
                .andExpect(jsonPath("$.content[0].active").value(false));
    }
}
