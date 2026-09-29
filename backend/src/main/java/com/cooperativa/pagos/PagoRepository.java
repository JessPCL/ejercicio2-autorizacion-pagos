package com.cooperativa.pagos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PagoRepository extends JpaRepository<PagoEntity, Long> {
    PagoEntity findByIdempotencyKey(String idempotencyKey);
}