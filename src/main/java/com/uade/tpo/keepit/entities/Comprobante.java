package com.uade.tpo.keepit.entities;

import java.sql.Date;

import com.uade.tpo.keepit.enums.Categoria;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@DiscriminatorValue("comprobante")
public class Comprobante extends Archivo {
    @Column(nullable = false)
    private Date fechaCreacion;
    @Column(nullable = false)
    private Double monto;
    @Enumerated(EnumType.STRING)
    private Categoria tipoCategoria;

    public Comprobante() {
    }

    public Comprobante(String nombre, Long tamanioBytes, String tipoExtension, Date fechaCreacion, Double monto,
            Categoria tipoCategoria) {
        super(nombre, tamanioBytes, tipoExtension);
        this.fechaCreacion = fechaCreacion;
        this.monto = monto;
        this.tipoCategoria = tipoCategoria;
    }
}
