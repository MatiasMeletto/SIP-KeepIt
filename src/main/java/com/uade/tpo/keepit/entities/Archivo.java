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
@DiscriminatorColumn(name = "tipoArchivo", discriminatorType = DiscriminatorType.STRING)
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
    private String tipoExtension; // esto no es un enum y es un string porque conviene guardarlo con la extension
                                  // para recuperar el archivo mas facilmente

    @Column(nullable = false)
    private Long tamanioBytes;

    @Column(nullable = false)
    private Boolean eliminado = false;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    public Archivo() {
    }

    public Archivo(String nombre, Long tamanioBytes, String tipoExtension, Usuario user) {
        this.nombre = nombre;
        this.tamanioBytes = tamanioBytes;
        this.tipoExtension = tipoExtension;
        this.usuario = user;
    }
}
