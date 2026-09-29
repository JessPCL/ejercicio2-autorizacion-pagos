package com.cooperativa.pagos;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagos")
public class PagoEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String idempotencyKey; // Clave para evitar cobrar dos veces

    private String cuentaDestino;
    private Double monto;
    private String estado; // APROBADO, RECHAZADO, PENDIENTE
    private LocalDateTime fechaCreacion;

    public PagoEntity() {}

    public PagoEntity(String idempotencyKey, String cuentaDestino, Double monto, String estado) {
        this.idempotencyKey = idempotencyKey;
        this.cuentaDestino = cuentaDestino;
        this.monto = monto;
        this.estado = estado;
        this.fechaCreacion = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getEstado() { return estado; }
}