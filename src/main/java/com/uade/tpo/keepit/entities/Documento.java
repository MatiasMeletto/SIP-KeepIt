package com.uade.tpo.keepit.entities;

import java.sql.Date;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@DiscriminatorValue("documento")
@EqualsAndHashCode(callSuper = true)
@Entity
@Data
public class Documento extends Archivo {
    @Column(nullable = false)
    private Date fechaSubida;
    @Column(nullable = false)
    private Date fechaVencimiento;

    public Documento() {
    }

    public Documento(String nombre, Long tamanioBytes, String tipoExtension, Usuario user, Date fechaSubida,
            Date fechaVencimiento) {
        super(nombre, tamanioBytes, tipoExtension, user);
        this.fechaSubida = fechaSubida;
        this.fechaVencimiento = fechaVencimiento;
    }
}
