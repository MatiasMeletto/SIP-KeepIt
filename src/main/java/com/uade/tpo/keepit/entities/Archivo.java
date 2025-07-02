package com.uade.tpo.keepit.entities;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.web.multipart.MultipartFile;

import com.uade.tpo.keepit.enums.Estado;

import java.io.IOException;
import java.time.LocalDateTime;

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
    private String contenido; // Ahora es un String Base64

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

    @Column
    private Estado estado = Estado.PENDIENTE;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaSubida;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = true)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "directorio_id")
    private Directorio directorio;

    public Archivo() {
    }

    // Constructor para MultipartFile (si lo usás en algún lado)
    public Archivo(MultipartFile archivo, Usuario user) throws IOException {
        this.setNombre(archivo.getOriginalFilename());
        this.setContenido(java.util.Base64.getEncoder().encodeToString(archivo.getBytes()));
        this.setTamanioBytes(archivo.getSize());
        this.setTipoExtension(archivo.getContentType());
        this.setUsuario(user);
        this.setEliminado(false);
    }

    public Archivo(MultipartFile archivo, Usuario user, String nombreTitular, String direccionTitular, 
                   String emailTitular, String telefonoTitular, String documentoIdentidadTitular) throws IOException {
        this(archivo, user); // Llamar al constructor anterior para evitar duplicación
        this.setNombreTitular(nombreTitular);
        this.setDireccionTitular(direccionTitular);
        this.setEmailTitular(emailTitular);
        this.setTelefonoTitular(telefonoTitular);
        this.setDocumentoIdentidadTitular(documentoIdentidadTitular);
    }
}
