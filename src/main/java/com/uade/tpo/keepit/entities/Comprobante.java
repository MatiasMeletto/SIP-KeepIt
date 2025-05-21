package com.uade.tpo.keepit.entities;

import java.sql.Date;

import com.uade.tpo.keepit.enums.Categoria;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
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
    @Column(nullable = false)
    private Categoria tipoCategoria;

    public Comprobante() {
    }

    public Comprobante(String nombre, Long tamanioBytes, String tipoArchivo, Date fechaCreacion, Double monto,
            Categoria tipoCategoria) {
        super(nombre, tamanioBytes, tipoArchivo);
        this.fechaCreacion = fechaCreacion;
        this.monto = monto;
        this.tipoCategoria = tipoCategoria;
    }
}
