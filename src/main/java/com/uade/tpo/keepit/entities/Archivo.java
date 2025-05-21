package com.uade.tpo.keepit.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;

@Data
@Inheritance(strategy = InheritanceType.JOINED) // esto cambia segun la forma de login q terminemos usando
@DiscriminatorColumn(name = "tipo_archivo", discriminatorType = DiscriminatorType.STRING)
@Entity
public abstract class Archivo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = true, unique = true)
    private String pathLocal;

    @Column(nullable = true, unique = true)
    private String pathApp;

    @Column(nullable = false)
    private String tipoArchivo;

    @Column(nullable = false)
    private Long tamanioBytes;

    @Column(nullable = false)
    private Boolean eliminado = false;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    public Archivo() {
    }

    public Archivo(String nombre, Long tamanioBytes, String tipoArchivo) {
        this.nombre = nombre;
        this.tamanioBytes = tamanioBytes;
        this.tipoArchivo = tipoArchivo;
    }
}
