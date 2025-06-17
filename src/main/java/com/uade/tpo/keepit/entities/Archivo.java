package com.uade.tpo.keepit.entities;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

@Data
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "tipoArchivo", discriminatorType = DiscriminatorType.STRING)
@Entity
public abstract class Archivo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] contenido;

    @Column(nullable = false)
    private String tipoExtension;

    @Column(nullable = false)
    private Long tamanioBytes;

    @Column(nullable = false)
    private Boolean eliminado = false;

    @Column
    private String nombreTitular;

    @Column
    private String direccionTitular;

    @Column
    private String emailTitular;

    @Column
    private String telefonoTitular;

    @Column
    private String documentoIdentidadTitular;

    @Column
    private String carpeta;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "directorio_id")
    private Directorio directorio;

    public Archivo() {
    }

    public Archivo(MultipartFile archivo, Usuario user) throws IOException {
        this.nombre = archivo.getOriginalFilename();
        this.contenido = archivo.getBytes();
        this.tamanioBytes = archivo.getSize();
        this.tipoExtension = archivo.getContentType();
        this.usuario = user;
    }

    public Archivo(MultipartFile archivo, Usuario user, String nombreTitular, String direccionTitular, 
                   String emailTitular, String telefonoTitular, String documentoIdentidadTitular) throws IOException {
        this.nombre = archivo.getOriginalFilename();
        this.contenido = archivo.getBytes();
        this.tamanioBytes = archivo.getSize();
        this.tipoExtension = archivo.getContentType();
        this.usuario = user;
        this.nombreTitular = nombreTitular;
        this.direccionTitular = direccionTitular;
        this.emailTitular = emailTitular;
        this.telefonoTitular = telefonoTitular;
        this.documentoIdentidadTitular = documentoIdentidadTitular;
    }

    public String getContenidoBase64() {
        return Base64.getEncoder().encodeToString(this.contenido);
    }
}
