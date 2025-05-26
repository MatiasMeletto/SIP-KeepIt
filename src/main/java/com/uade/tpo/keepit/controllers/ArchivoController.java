package com.uade.tpo.keepit.controllers;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.uade.tpo.keepit.DTO.ArchivoDTO;
import com.uade.tpo.keepit.entities.Archivo;
import com.uade.tpo.keepit.entities.Usuario;
import com.uade.tpo.keepit.exceptions.ArchivoDuplicadoException;
import com.uade.tpo.keepit.repository.UserRepository;
import com.uade.tpo.keepit.service.ArchivoService;

@RestController
@RequestMapping("/api/archivo")
public class ArchivoController {
    @Autowired
    private ArchivoService archivoService;

    @Autowired
    private UserRepository usuarioRepository;

    @GetMapping
    public ResponseEntity<List<ArchivoDTO>> getArchivos() {
        return ResponseEntity.ok(archivoService.getArchivos());
    }

    @GetMapping("/{archivoId}")
    public ResponseEntity<ArchivoDTO> getArchivoById(@PathVariable Long archivoId) {
        Optional<Archivo> result = archivoService.getArchivoById(archivoId);
        if (result.isPresent())
            return ResponseEntity.ok(archivoService.toDto(result.get()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Object> createArchivo(
            @RequestParam("file") MultipartFile file,
            @RequestParam("nombre") String nombre,
            @RequestParam("tamanioBytes") Long tamanioBytes,
            @RequestParam("tipoArchivo") String tipoArchivo,
            @RequestParam("fechaSubida") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.util.Date fechaSubida,
            @RequestParam("fechaVencimiento") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.util.Date fechaVencimiento,
            Principal principal)
            throws ArchivoDuplicadoException {

        Usuario usuario = usuarioRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Archivo result = archivoService.createArchivo(
                nombre,
                tamanioBytes,
                tipoArchivo,
                new java.sql.Date(fechaSubida.getTime()),
                new java.sql.Date(fechaVencimiento.getTime()),
                usuario);

        // Guardar archivo en disco
        try {
            String uploadDir = "uploads";
            java.nio.file.Path uploadPath = java.nio.file.Paths.get(uploadDir);
            if (!java.nio.file.Files.exists(uploadPath)) {
                java.nio.file.Files.createDirectories(uploadPath);
            }

            java.nio.file.Path filePath = uploadPath.resolve(file.getOriginalFilename());
            file.transferTo(filePath.toFile());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error al guardar el archivo en disco: " + e.getMessage());
        }

        return ResponseEntity
                .created(URI.create("/archivos/" + result.getId()))
                .body(result);
    }
}
