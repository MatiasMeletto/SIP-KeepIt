package com.uade.tpo.keepit.controllers;

import com.uade.tpo.keepit.DTO.DirectorioDTO;
import com.uade.tpo.keepit.entities.Usuario;
import com.uade.tpo.keepit.service.DirectorioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
    public ResponseEntity<List<DirectorioDTO>> obtenerTodos(Authentication authentication) {
        Usuario usuario = (Usuario) authentication.getPrincipal();
        return ResponseEntity.ok(directorioService.obtenerByEmail(usuario.getEmail()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DirectorioDTO> obtenerPorId(@PathVariable Long id) {
        Optional<DirectorioDTO> directorio = directorioService.obtenerPorId(id);
        return directorio.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<DirectorioDTO> crear(@RequestBody Map<String, Object> datos) {
        String nombre = (String) datos.get("nombre");
        Long padreId = null;

        if (datos.containsKey("padreId")) {
            padreId = Long.valueOf(datos.get("padreId").toString());
        }

        DirectorioDTO nuevo = directorioService.toDto(directorioService.crearDirectorio(nombre, padreId));
        return ResponseEntity.ok(nuevo);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id, Authentication authentication) {
        try {
            boolean eliminado = directorioService.eliminarDirectorio(id);
            if (eliminado) {
                return ResponseEntity.ok(Map.of("mensaje", "Directorio eliminado correctamente"));
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "No se puede eliminar: el directorio contiene archivos o subdirectorios"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al eliminar directorio: " + e.getMessage()));
        }
    }
}