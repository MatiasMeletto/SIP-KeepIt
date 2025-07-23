package com.uade.tpo.keepit.service;

import java.util.List;
import java.util.Optional;

import org.springframework.web.multipart.MultipartFile;

import com.uade.tpo.keepit.DTO.ArchivoDTO;
import com.uade.tpo.keepit.DTO.ClasificacionCompletaDTO; // ✅ AGREGAR ESTE IMPORT
import com.uade.tpo.keepit.entities.Archivo;
import com.uade.tpo.keepit.entities.Usuario;
import com.uade.tpo.keepit.exceptions.ArchivoDuplicadoException;

public interface ArchivoService {
    public List<ArchivoDTO> getArchivos();

    public Optional<Archivo> getArchivoById(Long archivoId);

    public Archivo createArchivo(MultipartFile file, Usuario user)
            throws ArchivoDuplicadoException;

    public Archivo saveArchivo(Archivo archivo);

    public ArchivoDTO toDto(Archivo archivo);

    // Métodos adicionales necesarios
    List<ArchivoDTO> obtenerArchivosPorUsuario(Usuario usuario);
    Optional<Archivo> obtenerArchivoByIdYUsuario(Long archivoId, Usuario usuario);
    Archivo crearArchivoDesdeClasificacion(ClasificacionCompletaDTO clasificacion, Usuario usuario);
    Long contarArchivosPorUsuario(Usuario usuario);

    public Void eliminarArchivo(Archivo archivo);
    public ArchivoDTO actualizarArchivo(Archivo archivo)
            throws ArchivoDuplicadoException;

    public List<ArchivoDTO> getArchivosConVencimientosCercanos();
    
    /**
     * Obtiene archivos con vencimientos cercanos para un usuario específico
     */
    public List<ArchivoDTO> getArchivosConVencimientosCercanosPorUsuario(Usuario usuario);
    
    /**
     * Obtiene archivos que vencen hoy para un usuario específico
     */
    public List<ArchivoDTO> getArchivosQueVencenHoyPorUsuario(Usuario usuario);
    
    /**
     * Obtiene archivos vencidos para un usuario específico
     */
    public List<ArchivoDTO> getArchivosVencidosPorUsuario(Usuario usuario);
}
