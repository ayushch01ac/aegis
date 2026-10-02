package com.aegis.route;

import static org.assertj.core.api.Assertions.assertThat;

import com.aegis.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RouteRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private RouteRepository routeRepository;

    @Test
    void saveAndFindByName() {
        Route route = new Route("orders-service", "http://orders:8080", 2000, RoutePriority.HIGH, true);
        routeRepository.saveAndFlush(route);

        assertThat(routeRepository.findByName("orders-service")).isPresent();
        assertThat(routeRepository.existsByName("orders-service")).isTrue();
        assertThat(routeRepository.existsByNameAndIdNot("orders-service", route.getId())).isFalse();
    }
}
