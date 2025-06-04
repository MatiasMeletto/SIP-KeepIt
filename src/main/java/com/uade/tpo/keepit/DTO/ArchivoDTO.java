package com.uade.tpo.keepit.DTO;

import java.sql.Date;

import com.uade.tpo.keepit.entities.Usuario;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArchivoDTO {
    private Long id;
    private String nombre;
    private String tipoArchivo;
    private Long tamanioBytes;
    private Date fechaSubida;
    private Date fechaVencimiento;
    private Usuario user;
}