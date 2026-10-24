package com.aegis.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class DownstreamHttpClientTest {

    @Test
    void removesStandardAndConnectionNominatedHopByHopHeaders() {
        HttpHeaders incoming = new HttpHeaders();
        incoming.set(HttpHeaders.HOST, "aegis.example");
        incoming.set(HttpHeaders.CONNECTION, "Keep-Alive, X-Connection-Only");
        incoming.set("Keep-Alive", "timeout=5");
        incoming.set("X-Connection-Only", "private");
        incoming.set("X-End-To-End", "preserve");

        HttpHeaders filtered = DownstreamHttpClient.filterHopByHopHeaders(incoming);

        assertThat(filtered.getValuesAsList(HttpHeaders.HOST)).isEmpty();
        assertThat(filtered.getValuesAsList(HttpHeaders.CONNECTION)).isEmpty();
        assertThat(filtered.getValuesAsList("Keep-Alive")).isEmpty();
        assertThat(filtered.getValuesAsList("X-Connection-Only")).isEmpty();
        assertThat(filtered.getFirst("X-End-To-End")).isEqualTo("preserve");
    }
}
