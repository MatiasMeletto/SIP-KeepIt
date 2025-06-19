package com.uade.tpo.keepit.service;

import com.uade.tpo.keepit.DTO.DirectorioDTO;
import com.uade.tpo.keepit.DTO.OrganizacionDirectoriosDTO;
import com.uade.tpo.keepit.entities.Directorio;
import com.uade.tpo.keepit.entities.Usuario;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface DirectorioService {

    Directorio crearOObtenerDirectorio(String nombre, Directorio padre);
    
    List<Directorio> obtenerDirectoriosRaiz();
    
    List<Directorio> obtenerTodos();
    
    Optional<Directorio> obtenerPorId(Long id);
    
    Directorio crearDirectorio(String nombre, Long padreId);
    
    // Método nuevo
    Directorio guardarDirectorio(Directorio directorio);
    
    // Método nuevo
    List<Directorio> obtenerTodosPorUsuario(Usuario usuario);
    
    // Método nuevo
    Optional<Directorio> buscarPorId(Long id);
    
    // Método nuevo
    boolean eliminarDirectorio(Long id);
    
    // Métodos nuevos para la estructura de directorios
    Map<String, Object> obtenerEstructuraDirectorios(Usuario usuario);
    
    // Nuevo método para sincronizar directorios con la respuesta de la IA
    Map<String, Object> sincronizarDirectorios(OrganizacionDirectoriosDTO organizacionDTO, Usuario usuario);
    
    // Método para preparar la estructura de directorios para la IA
    List<DirectorioDTO> obtenerDirectoriosParaIA(Usuario usuario);
    
    // Añadir este método
    Directorio obtenerPorNombre(String nombre, Usuario usuario);
    
    // Añadir este método para obtener solo directorios con archivos
    List<Map<String, Object>> obtenerDirectoriosConArchivos(Usuario usuario);
}