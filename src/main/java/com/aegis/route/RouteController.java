package com.aegis.route;

import com.aegis.common.web.PagedResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping
    public ResponseEntity<RouteResponse> create(@Valid @RequestBody RouteRequest request) {
        RouteResponse created = routeService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/routes/" + created.id())).body(created);
    }

    @GetMapping
    public PagedResponse<RouteResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return routeService.list(pageable);
    }

    @GetMapping("/{id}")
    public RouteResponse get(@PathVariable UUID id) {
        return routeService.get(id);
    }

    @PutMapping("/{id}")
    public RouteResponse update(@PathVariable UUID id, @Valid @RequestBody RouteRequest request) {
        return routeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        routeService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
