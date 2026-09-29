package com.cooperativa.pagos;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/v1/pagos")
public class PagoController {

    // Simulación rápida de un repositorio/servicio integrado para este ejercicio
    @Autowired
    private PagoRepository repository;

    @PostMapping
    public ResponseEntity<?> procesarPago(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody Map<String, Object> payload) {
        
        String cuenta = payload.get("cuentaDestino").toString();
        Double monto = Double.parseDouble(payload.get("monto").toString());

        // 1. Verificar Idempotencia (¿Ya procesamos este pago antes?)
        if (repository.findByIdempotencyKey(idempotencyKey) != null) {
            Map<String, String> error = new HashMap<>();
            error.put("mensaje", "Pago duplicado detectado (Idempotency-Key ya existe).");
            return ResponseEntity.badRequest().body(error);
        }

        // 2. Validación Síncrona (Límites de cuenta)
        if (monto > 1000.0) {
            PagoEntity fallido = new PagoEntity(idempotencyKey, cuenta, monto, "RECHAZADO_LIMITE");
            repository.save(fallido);
            Map<String, String> error = new HashMap<>();
            error.put("mensaje", "El monto supera el límite permitido de $1000.");
            return ResponseEntity.badRequest().body(error);
        }

        // 3. Registrar éxito y procesar (Acá entraría RabbitMQ para notificar en la vida real)
        PagoEntity exitoso = new PagoEntity(idempotencyKey, cuenta, monto, "APROBADO");
        repository.save(exitoso);

        Map<String, Object> response = new HashMap<>();
        response.put("pagoId", exitoso.getId());
        response.put("estado", exitoso.getEstado());
        
        return ResponseEntity.ok(response);
    }
}