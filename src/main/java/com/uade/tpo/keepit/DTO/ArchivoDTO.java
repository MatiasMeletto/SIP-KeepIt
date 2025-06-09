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
    private String nombre;
    private String tipoArchivo;
    private String pathApp;
    private String path;
    private Long tamanioBytes;
    private Date fechaSubida;
    private Date fechaVencimiento;
    private Usuario user;
}