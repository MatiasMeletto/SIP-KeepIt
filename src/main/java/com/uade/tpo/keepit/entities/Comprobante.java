package com.uade.tpo.keepit.entities;

import java.io.IOException;
import java.sql.Date;

import com.uade.tpo.keepit.enums.Categoria;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.web.multipart.MultipartFile;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@DiscriminatorValue("comprobante")
public class Comprobante extends Archivo {
    @Column(nullable = false)
    private Double monto;
    @Enumerated(EnumType.STRING)
    private Categoria tipoCategoria;

    public Comprobante() {
    }

    public Comprobante(MultipartFile archivo, Usuario user, String nombreTitular, String direccionTitular, 
                       String emailTitular, String telefonoTitular, String documentoIdentidadTitular,
                       Date fechaCreacion, Double monto, Categoria tipoCategoria) throws IOException {
        super(archivo, user, nombreTitular, direccionTitular, emailTitular, telefonoTitular, documentoIdentidadTitular);
        this.monto = monto;
        this.tipoCategoria = tipoCategoria;
    }
}
