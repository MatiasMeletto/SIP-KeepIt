package com.uade.tpo.keepit.entities;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.Data;

@Entity
@Data
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String password;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false, unique = true)
    private String telefono;

    @OneToMany(mappedBy = "usuario")
    private List<Archivo> archivos;

    public Usuario() {
    }

    public Usuario(String password, String email, String telefono) {
        this.password = password;
        this.email = email;
        this.telefono = telefono;
    }
}
