package com.uade.tpo.keepit.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ClasificacionResult {
    private String carpeta;           // "vehiculos/seguros", "comprobantes/facturas"
    private String tipoArchivo;       // "DOCUMENTO" o "COMPROBANTE"
    private Double monto;             // Para comprobantes
    private java.sql.Date fechaVencimiento; // Para documentos
    private java.sql.Date fechaCreacion;    // Para ambos
    private String categoria;         // Para comprobantes (enum)
}