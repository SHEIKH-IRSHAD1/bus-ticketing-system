package com.bustransit.ticketing.repository;

import com.bustransit.ticketing.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByProviderRef(String providerRef);
}
