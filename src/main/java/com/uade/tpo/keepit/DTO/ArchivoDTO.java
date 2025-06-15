package com.uade.tpo.keepit.DTO;

import java.sql.Date;

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
    private String carpeta;
    
    // Campos específicos para Comprobante
    private Double monto;
    private String categoria;
    private Date fechaCreacion;
    
    // Campos específicos para Documento
    private Date fechaSubida;
    private Date fechaVencimiento;
    
    // Tipo de archivo (para saber si es Documento o Comprobante)
    private String tipoArchivo;
}