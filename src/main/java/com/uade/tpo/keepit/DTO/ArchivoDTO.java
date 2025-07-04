package com.uade.tpo.keepit.DTO;

import java.sql.Date;

import com.uade.tpo.keepit.enums.Estado;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArchivoDTO {
    private Long id;
    private String nombre;
    private String tipoExtension;
    private Long tamanioBytes;
    private Boolean eliminado;
    private String contenidoBase64;

    // Campos del titular
    private String nombreTitular;
    private String direccionTitular;
    private String emailTitular;
    private String telefonoTitular;
    private String documentoIdentidadTitular;

    // Campo de carpeta automática
    private String directorioNombre;
    private Long directorioId;

    // Tipo de archivo (para saber si es Documento o Comprobante O Factura)
    private String tipoArchivo;

    // Campos específicos para Comprobante
    private Double monto;
    private String categoria;
    private Date fechaCreacion;

    // Campos específicos para Documento
    private Date fechaSubida;
    private Date fechaVencimiento;

    // ===== AGREGAR ESTOS CAMPOS PARA FACTURA =====
    private String numeroFactura;
    private Date fechaEmision;
    private String emisor;
    private String tipoFactura;

    private Estado estado;
}