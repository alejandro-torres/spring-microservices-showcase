package com.showcase.common.infrastructure.rest.filter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class FilterParametersArgumentResolverTest {

    @RestController
    static class EchoController {

        @GetMapping("/echo")
        String echo(FilterParameters filter) {
            return String.join("|", filter.values());
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new EchoController())
            .setCustomArgumentResolvers(new FilterParametersArgumentResolver())
            .build();

    @Test
    @DisplayName("receives each filter without splitting the values of in and between by commas")
    void resolve_shouldKeepCommasInsideEachFilter() throws Exception {
        mockMvc.perform(get("/echo")
                        .param("filter", "categoryId:in:1,2,3")
                        .param("filter", "price:between:100,500"))
                .andExpect(content().string("categoryId:in:1,2,3|price:between:100,500"));
    }

    @Test
    @DisplayName("returns an empty list if there are no filters")
    void resolve_withoutFilters_shouldReturnEmpty() throws Exception {
        mockMvc.perform(get("/echo")).andExpect(content().string(""));
    }
}
