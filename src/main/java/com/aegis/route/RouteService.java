package com.aegis.route;

import com.aegis.common.web.PagedResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RouteService {

    private final RouteRepository routeRepository;

    public RouteService(RouteRepository routeRepository) {
        this.routeRepository = routeRepository;
    }

    @Transactional
    public RouteResponse create(RouteRequest request) {
        if (routeRepository.existsByName(request.name())) {
            throw new DuplicateRouteNameException(request.name());
        }
        Route route = new Route(
                request.name(),
                request.baseUrl(),
                request.timeoutMs(),
                request.priority(),
                request.enabledOrDefault());
        return RouteResponse.from(routeRepository.save(route));
    }

    @Transactional(readOnly = true)
    public PagedResponse<RouteResponse> list(Pageable pageable) {
        return PagedResponse.from(routeRepository.findAll(pageable).map(RouteResponse::from));
    }

    @Transactional(readOnly = true)
    public RouteResponse get(UUID id) {
        return RouteResponse.from(findOrThrow(id));
    }

    @Transactional
    public RouteResponse update(UUID id, RouteRequest request) {
        Route route = findOrThrow(id);
        if (routeRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new DuplicateRouteNameException(request.name());
        }
        route.applyUpdate(
                request.name(),
                request.baseUrl(),
                request.timeoutMs(),
                request.priority(),
                request.enabledOrDefault());
        return RouteResponse.from(route);
    }

    @Transactional
    public void delete(UUID id) {
        Route route = findOrThrow(id);
        routeRepository.delete(route);
    }

    private Route findOrThrow(UUID id) {
        return routeRepository
                .findById(id)
                .orElseThrow(() -> new RouteNotFoundException("Route not found: " + id));
    }
}
