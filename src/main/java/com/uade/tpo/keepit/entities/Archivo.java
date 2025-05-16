package com.uade.tpo.keepit.entities;

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
    @Column(nullable = false, unique = true)
    private String pathLocal;
    @Column(nullable = false, unique = true)
    private String pathApp;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    
    public Archivo() {
    }

    public Archivo(String nombre, String pathLocal, String pathApp) {
        this.nombre = nombre;
        this.pathLocal = pathLocal;
        this.pathApp = pathApp;
    }
}
