package com.bustransit.dispatch.repository;

import com.bustransit.dispatch.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<Route, Long> {
}
