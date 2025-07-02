package com.uade.tpo.keepit.entities;

import java.io.IOException;
import java.sql.Date;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.web.multipart.MultipartFile;

@DiscriminatorValue("documento")
@EqualsAndHashCode(callSuper = true)
@Entity
@Data
public class Documento extends Archivo {
    @Column(nullable = true)
    private Date fechaVencimiento;

    public Documento() {
    }

    public Documento(MultipartFile archivo, Usuario user, String nombreTitular, String direccionTitular, 
                     String emailTitular, String telefonoTitular, String documentoIdentidadTitular,
                     Date fechaVencimiento) throws IOException {
        super(archivo, user, nombreTitular, direccionTitular, emailTitular, telefonoTitular, documentoIdentidadTitular);
        this.fechaVencimiento = fechaVencimiento;
    }
}
