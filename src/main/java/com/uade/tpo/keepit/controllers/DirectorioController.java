package com.uade.tpo.keepit.controllers;

import com.uade.tpo.keepit.entities.Directorio;
import com.uade.tpo.keepit.service.DirectorioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/directorios")
public class DirectorioController {
    
    @Autowired
    private DirectorioService directorioService;
    
    @GetMapping
    public ResponseEntity<List<Directorio>> obtenerTodos() {
        return ResponseEntity.ok(directorioService.obtenerTodos());
    }
    
    @GetMapping("/raiz")
    public ResponseEntity<List<Directorio>> obtenerRaices() {
        return ResponseEntity.ok(directorioService.obtenerDirectoriosRaiz());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Directorio> obtenerPorId(@PathVariable Long id) {
        Optional<Directorio> directorio = directorioService.obtenerPorId(id);
        return directorio.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    @PostMapping
    public ResponseEntity<Directorio> crear(@RequestBody Map<String, Object> datos) {
        String nombre = (String) datos.get("nombre");
        Long padreId = null;
        
        if (datos.containsKey("padreId")) {
            padreId = Long.valueOf(datos.get("padreId").toString());
        }
        
        Directorio nuevo = directorioService.crearDirectorio(nombre, padreId);
        return ResponseEntity.ok(nuevo);
    }
}