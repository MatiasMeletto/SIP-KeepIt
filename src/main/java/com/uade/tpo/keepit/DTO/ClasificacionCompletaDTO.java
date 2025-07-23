package com.uade.tpo.keepit.DTO;

import java.sql.Date;
import java.util.List;
import com.uade.tpo.keepit.enums.Categoria;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClasificacionCompletaDTO {
    // Campos básicos del archivo
    private String nombre;
    private String nombreArchivo; // Añadido para mantener compatibilidad con ambos nombres
    private String tipoExtension;
    private Long tamanioBytes;
    private String contenidoArchivo; // En base64
    
    // Campos para organización
    private String resumen;
    
    // Campos del titular
    private String nombreTitular;
    private String direccionTitular;
    private String emailTitular;
    private String telefonoTitular;
    private String documentoIdentidadTitular;
    
    // Campos para Comprobante
    private Double monto;
    private Categoria tipoCategoria;
    
    // Campos para Documento
    private Date fechaVencimiento;
    
    // Campos para Factura
    private String numeroFactura;
    private Date fechaEmision;
    private String emisor;
    private String tipoFactura;
    
    // Decisión de directorio
    private Long directorioId;
    private String directorioNombre;
    private String accionTomada;
    
    // Información adicional
    private String tipoArchivoDetectado; // "DOCUMENTO", "COMPROBANTE", "FACTURA"
    private String explicacionIA;
    private String nombreRecomendado; // Nombre recomendado por la IA
    
    // Getter para mantener compatibilidad con ambos nombres
    public String getNombreArchivo() {
        return nombreArchivo != null ? nombreArchivo : nombre;
    }
    
    // Setter para mantener compatibilidad con ambos nombres
    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
        this.nombre = nombreArchivo;
    }
}