package com.uade.tpo.keepit.entities;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;
import java.util.ArrayList;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Directorio {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    @Builder.Default
    private Boolean eliminado = false;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "padre_id")
    private Directorio padre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;
    
    @OneToMany(mappedBy = "padre", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Directorio> subdirectorios = new ArrayList<>();
    
    @OneToMany(mappedBy = "directorio")
    @Builder.Default
    private List<Archivo> archivos = new ArrayList<>();
    
    // Método helper para agregar subdirectorio
    public void agregarSubdirectorio(Directorio subdirectorio) {
        subdirectorios.add(subdirectorio);
        subdirectorio.setPadre(this);
    }
    
    // Obtener ruta completa
    public String getRutaCompleta() {
        if (padre == null) {
            return "/" + nombre;
        } else {
            return padre.getRutaCompleta() + "/" + nombre;
        }
    }
}