package com.uade.tpo.keepit.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ContentDisposition;

import com.uade.tpo.keepit.DTO.ArchivoDTO;
import com.uade.tpo.keepit.entities.Archivo;
import com.uade.tpo.keepit.entities.Usuario;
import com.uade.tpo.keepit.exceptions.ArchivoDuplicadoException;
import com.uade.tpo.keepit.service.ArchivoService;
import com.uade.tpo.keepit.service.UsuarioService;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/archivo")
public class ArchivoController {
    @Autowired
    private ArchivoService archivoService;

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping
    public ResponseEntity<List<ArchivoDTO>> getArchivos() {
        return ResponseEntity.ok(archivoService.getArchivos());
    }

    @GetMapping("/{archivoId}")
    public ResponseEntity<ArchivoDTO> getArchivoById(@PathVariable Long archivoId) {
        Optional<Archivo> result = archivoService.getArchivoById(archivoId);
        if (result.isPresent()) {
            Archivo archivo = result.get();
            ArchivoDTO dto = archivoService.toDto(archivo);
            return ResponseEntity.ok(dto);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{archivoId}/download")
    public ResponseEntity<byte[]> downloadArchivo(@PathVariable Long archivoId) {
        Optional<Archivo> result = archivoService.getArchivoById(archivoId);
        if (result.isPresent()) {
            Archivo archivo = result.get();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(archivo.getTipoExtension()));
            headers.setContentDisposition(ContentDisposition.attachment()
                .filename(archivo.getNombreArchivo())
                .build());
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(archivo.getContenido());
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Object> createArchivo(
            @RequestParam("file") MultipartFile file,
            Principal principal) throws ArchivoDuplicadoException {

        try {
            Usuario usuario = usuarioService.getUsuarioByEmail(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            Archivo result = archivoService.createArchivo(file, usuario);
            
            return ResponseEntity.ok(archivoService.toDto(result));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error al guardar el archivo: " + e.getMessage());
        }
    }
}
