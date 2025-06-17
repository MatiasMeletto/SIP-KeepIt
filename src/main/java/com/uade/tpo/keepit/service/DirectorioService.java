package com.uade.tpo.keepit.service;

import com.uade.tpo.keepit.entities.Directorio;
import java.util.List;
import java.util.Optional;

public interface DirectorioService {
    
    void inicializarDirectorios();
    
    Directorio crearOObtenerDirectorio(String nombre, Directorio padre);
    
    List<Directorio> obtenerDirectoriosRaiz();
    
    List<Directorio> obtenerTodos();
    
    Optional<Directorio> obtenerPorId(Long id);
    
    Directorio crearDirectorio(String nombre, Long padreId);
}