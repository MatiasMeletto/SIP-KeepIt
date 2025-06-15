package com.uade.tpo.keepit.DTO;

import java.sql.Date;

import com.google.auto.value.AutoValue.Builder;
import com.uade.tpo.keepit.entities.Usuario;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArchivoDTO {
    private Long id;
    private String nombreArchivo;
    private String tipoArchivo;
    private String pathApp;
    private String path;
    private Long tamanioBytes;
    private Date fechaSubida;
    private Date fechaVencimiento;
    private Usuario user;
    private Boolean eliminado;
    private String tipoExtension;
    private String contenidoBase64;
    private String nombre;    // Asegúrate de que tu ArchivoDTO tenga estos campos:
    private String nombreTitular;
    private String direccionTitular;
    private String emailTitular;
    private String telefonoTitular;
    private String documentoIdentidadTitular;
}