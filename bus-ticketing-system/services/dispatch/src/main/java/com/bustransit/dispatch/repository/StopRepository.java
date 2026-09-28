package com.bustransit.dispatch.repository;

import com.bustransit.dispatch.entity.Stop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StopRepository extends JpaRepository<Stop, Long> {

    List<Stop> findByRouteId(Long routeId);
}
