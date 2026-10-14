package com.showcase.product.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.showcase.product.domain.exception.ProductErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ProductMessagesTest {

    @ParameterizedTest
    @EnumSource(ProductErrorCode.class)
    @DisplayName("todos los códigos de error de product-service tienen mensaje en español y en inglés")
    void allCodes_shouldHaveSpanishAndEnglishMessages(ProductErrorCode errorCode) throws IOException {
        assertThat(load("messages.properties")).containsKey(errorCode.messageKey());
        assertThat(load("messages_en.properties")).containsKey(errorCode.messageKey());
    }

    private Properties load(String path) throws IOException {
        var properties = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
