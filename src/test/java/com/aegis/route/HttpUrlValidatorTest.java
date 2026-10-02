package com.aegis.route;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HttpUrlValidatorTest {

    private final HttpUrlValidator validator = new HttpUrlValidator();

    @Test
    void acceptsHttpAndHttpsUrls() {
        assertThat(validator.isValid("http://orders:8080", null)).isTrue();
        assertThat(validator.isValid("https://payments.example.com/api", null)).isTrue();
    }

    @Test
    void rejectsNonHttpSchemesAndMissingHost() {
        assertThat(validator.isValid("ftp://orders:8080", null)).isFalse();
        assertThat(validator.isValid("not-a-url", null)).isFalse();
        assertThat(validator.isValid("http:///no-host", null)).isFalse();
    }
}
