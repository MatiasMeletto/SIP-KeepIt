package com.uade.tpo.keepit.service;

import com.uade.tpo.keepit.entities.Directorio;
import com.uade.tpo.keepit.repository.DirectorioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;

@Service
public class DirectorioServiceImpl implements DirectorioService {
    
    @Autowired
    private DirectorioRepository directorioRepository;
    
    @PostConstruct
    public void inicializarDirectorios() {
        // Crear directorios base si no existen
        if (directorioRepository.findAllByPadreIsNull().isEmpty()) {
            Directorio documentos = crearOObtenerDirectorio("Documentos", null);
            Directorio facturas = crearOObtenerDirectorio("Facturas", documentos);
            Directorio recibos = crearOObtenerDirectorio("Recibos", documentos);
            Directorio comprobantes = crearOObtenerDirectorio("Comprobantes", null);
            Directorio bancarios = crearOObtenerDirectorio("Bancarios", comprobantes);
            Directorio impositivos = crearOObtenerDirectorio("Impositivos", comprobantes);
        }
    }
    
    @Override
    public Directorio crearOObtenerDirectorio(String nombre, Directorio padre) {
        Optional<Directorio> existente;
        
        if (padre == null) {
            existente = directorioRepository.findByNombreAndPadreIsNull(nombre);
        } else {
            existente = directorioRepository.findByNombreAndPadreId(nombre, padre.getId());
        }
        
        if (existente.isPresent()) {
            return existente.get();
        }
        
        Directorio nuevo = new Directorio();
        nuevo.setNombre(nombre);
        nuevo.setPadre(padre);
        return directorioRepository.save(nuevo);
    }
    
    @Override
    public List<Directorio> obtenerDirectoriosRaiz() {
        return directorioRepository.findRootDirectories();
    }
    
    @Override
    public List<Directorio> obtenerTodos() {
        return directorioRepository.findAll();
    }
    
    @Override
    public Optional<Directorio> obtenerPorId(Long id) {
        return directorioRepository.findById(id);
    }
    
    @Override
    public Directorio crearDirectorio(String nombre, Long padreId) {
        Directorio padre = null;
        if (padreId != null) {
            padre = directorioRepository.findById(padreId)
                    .orElseThrow(() -> new RuntimeException("Directorio padre no encontrado"));
        }
        return crearOObtenerDirectorio(nombre, padre);
    }
}