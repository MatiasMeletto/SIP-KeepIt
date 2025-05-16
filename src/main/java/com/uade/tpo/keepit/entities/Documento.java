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
    private Date fechaCreacion;
    @Column(nullable = false)
    private Date fechaVencimiento;

    public Documento() {
    }

    public Documento(String nombre, String pathLocal, String pathApp, Date fechaCreacion, Date fechaVencimiento) {
        super(nombre, pathLocal, pathApp);
        this.fechaCreacion = fechaCreacion;
        this.fechaVencimiento = fechaVencimiento;
    }  
}
