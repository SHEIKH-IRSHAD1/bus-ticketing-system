package com.bustransit.dispatch.controller;

import com.bustransit.dispatch.entity.Route;
import com.bustransit.dispatch.entity.Stop;
import com.bustransit.dispatch.repository.RouteRepository;
import com.bustransit.dispatch.repository.StopRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ReferenceDataController {

    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;

    public ReferenceDataController(RouteRepository routeRepository, StopRepository stopRepository) {
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
    }

    @GetMapping("/routes")
    public List<Route> listRoutes() {
        return routeRepository.findAll();
    }

    @GetMapping("/stops")
    public List<Stop> listStops() {
        return stopRepository.findAll();
    }
}
