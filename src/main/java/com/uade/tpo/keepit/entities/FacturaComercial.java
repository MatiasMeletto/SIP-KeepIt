package com.uade.tpo.keepit.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.sql.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@DiscriminatorValue("factura")
public class FacturaComercial extends Archivo {
    @Column(name = "numero_factura", length = 100)
    private String numeroFactura;
    
    @Column(name = "fecha_emision")
    private Date fechaEmision;
    
    @Column(name = "monto_total")
    private Double montoTotal;
    
    @Column(name = "emisor", length = 200)
    private String emisor; // Nombre de la empresa/persona que emite
    
    @Column(name = "tipo_factura", length = 10)
    private String tipoFactura; // A, B, C (solo los básicos)

    @Column(nullable = true)
    private Date fechaVencimiento;
}