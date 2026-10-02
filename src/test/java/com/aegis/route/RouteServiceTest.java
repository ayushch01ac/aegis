package com.aegis.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    @Mock
    private RouteRepository routeRepository;

    private RouteService routeService;

    @BeforeEach
    void setUp() {
        routeService = new RouteService(routeRepository);
    }

    @Test
    void createPersistsANewRoute() {
        when(routeRepository.existsByName("orders-service")).thenReturn(false);
        when(routeRepository.save(any(Route.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RouteResponse response = routeService.create(sampleRequest("orders-service"));

        ArgumentCaptor<Route> captor = ArgumentCaptor.forClass(Route.class);
        verify(routeRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("orders-service");
        assertThat(response.name()).isEqualTo("orders-service");
        assertThat(response.enabled()).isTrue();
    }

    @Test
    void createRejectsDuplicateNames() {
        when(routeRepository.existsByName("orders-service")).thenReturn(true);

        assertThatThrownBy(() -> routeService.create(sampleRequest("orders-service")))
                .isInstanceOf(DuplicateRouteNameException.class);
    }

    @Test
    void getThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(routeRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> routeService.get(id)).isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void listMapsEntitiesToResponses() {
        Route route = new Route("orders-service", "http://orders:8080", 2000, RoutePriority.HIGH, true);
        Page<Route> page = new PageImpl<>(List.of(route), PageRequest.of(0, 20), 1);
        when(routeRepository.findAll(PageRequest.of(0, 20))).thenReturn(page);

        var response = routeService.list(PageRequest.of(0, 20));

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).extracting(RouteResponse::name).containsExactly("orders-service");
    }

    private static RouteRequest sampleRequest(String name) {
        return new RouteRequest(name, "http://orders:8080", 2000, RoutePriority.HIGH, true);
    }
}
