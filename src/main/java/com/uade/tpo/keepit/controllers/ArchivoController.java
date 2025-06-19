package com.uade.tpo.keepit.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ContentDisposition;

import com.uade.tpo.keepit.DTO.ArchivoDTO;
import com.uade.tpo.keepit.DTO.ClasificacionCompletaDTO;
import com.uade.tpo.keepit.entities.Archivo;
import com.uade.tpo.keepit.entities.Directorio;
import com.uade.tpo.keepit.entities.Usuario;
import com.uade.tpo.keepit.service.ArchivoService;
import com.uade.tpo.keepit.service.DirectorioService;
import com.uade.tpo.keepit.service.OpenAIClasificadorService;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/archivos")
public class ArchivoController {

    @Autowired
    private ArchivoService archivoService;

    @Autowired
    private DirectorioService directorioService;

    @Autowired
    private OpenAIClasificadorService openAIClasificadorService;

    // Mantener los endpoints existentes...
    @GetMapping
    public ResponseEntity<List<ArchivoDTO>> obtenerArchivos(Authentication authentication) {
        try {
            Usuario usuario = (Usuario) authentication.getPrincipal();
            List<ArchivoDTO> archivos = archivoService.obtenerArchivosPorUsuario(usuario);
            return ResponseEntity.ok(archivos);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{archivoId}")
    public ResponseEntity<ArchivoDTO> obtenerArchivoPorId(
            @PathVariable Long archivoId,
            Authentication authentication) {
        try {
            Usuario usuario = (Usuario) authentication.getPrincipal();
            Optional<Archivo> archivo = archivoService.obtenerArchivoByIdYUsuario(archivoId, usuario);

            if (archivo.isPresent()) {
                ArchivoDTO dto = archivoService.toDto(archivo.get());
                return ResponseEntity.ok(dto);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{archivoId}/download")
    public ResponseEntity<byte[]> descargarArchivo(
            @PathVariable Long archivoId,
            Authentication authentication) {
        try {
            Usuario usuario = (Usuario) authentication.getPrincipal();
            Optional<Archivo> archivo = archivoService.obtenerArchivoByIdYUsuario(archivoId, usuario);

            if (archivo.isPresent()) {
                Archivo arch = archivo.get();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType(arch.getTipoExtension()));
                headers.setContentDisposition(ContentDisposition.attachment()
                        .filename(arch.getNombre())
                        .build());

                // Convertir el contenido (String) a byte[]
                byte[] contenidoBytes = arch.getContenido().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                return ResponseEntity.ok()
                        .headers(headers)
                        .body(contenidoBytes);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * ENDPOINT 1: Reconoce el tipo de documento y extrae campos mediante el Document Recognizer
     */
    @PostMapping("/reconocer")
    public ResponseEntity<?> reconocerDocumento(@RequestParam("file") MultipartFile file, Authentication authentication) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "El archivo está vacío"));
            }

            Usuario usuario = (Usuario) authentication.getPrincipal();
            
            // Reconocer documento con el servicio actualizado
            ClasificacionCompletaDTO resultado;
            try {
                resultado = openAIClasificadorService.reconocerDocumento(file, usuario);
            } catch (Exception e) {
                // Fallback si hay error con OpenAI
                resultado = new ClasificacionCompletaDTO();
                resultado.setNombre(file.getOriginalFilename());
                resultado.setTipoExtension(file.getContentType());
                resultado.setTamanioBytes(file.getSize());
                resultado.setContenidoArchivo(Base64.getEncoder().encodeToString(file.getBytes()));
                resultado.setTipoArchivoDetectado("DOCUMENTO");
                resultado.setResumen("No se pudo procesar con IA: " + e.getMessage());
            }
            
            return ResponseEntity.ok(resultado);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al reconocer documento: " + e.getMessage()));
        }
    }
    
    /**
     * ENDPOINT 2: Organiza directorios según la clasificación utilizando el Document Organizer
     * y sincroniza los directorios según la estructura recomendada.
     * Opcionalmente guarda el archivo en el directorio recomendado si guardar=true.
     */
    @PostMapping("/organizar")
    public ResponseEntity<?> organizarDocumento(@RequestBody ClasificacionCompletaDTO clasificacion, 
                                              @RequestParam(required = false, defaultValue = "true") Boolean guardar,
                                              Authentication authentication) {
        try {
            Usuario usuario = (Usuario) authentication.getPrincipal();
            
            if (clasificacion.getContenidoArchivo() == null || clasificacion.getNombre() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Faltan datos del archivo"));
            }
            
            // CAMBIO 1: Utilizar el servicio de OpenAI para sugerir la organización
            Map<String, Object> resultado = openAIClasificadorService.sugerirOrganizacion(clasificacion, usuario);
            
            // CAMBIO 2: Si se debe guardar el archivo, encontramos el directorio recomendado y lo guardamos
            if (guardar) {
                // Obtener el directorio adecuado desde la respuesta de la IA
                Long directorioId = null;
                if (resultado.containsKey("directorioRecomendado")) {
                    Map<String, Object> dirRecomendado = (Map<String, Object>) resultado.get("directorioRecomendado");
                    if (dirRecomendado != null && dirRecomendado.containsKey("id")) {
                        directorioId = ((Number) dirRecomendado.get("id")).longValue();
                    }
                }
                
                // Si no tenemos directorio recomendado, buscamos por tipo de archivo (fallback)
                if (directorioId == null) {
                    Directorio directorioSeleccionado = null;
                    
                    switch (clasificacion.getTipoArchivoDetectado()) {
                        case "FACTURA":
                            directorioSeleccionado = directorioService.obtenerPorNombre("Facturas", usuario);
                            break;
                        case "COMPROBANTE":
                            directorioSeleccionado = directorioService.obtenerPorNombre("Comprobantes", usuario);
                            break;
                        default:
                            directorioSeleccionado = directorioService.obtenerPorNombre("Documentos", usuario);
                    }
                    
                    if (directorioSeleccionado != null) {
                        directorioId = directorioSeleccionado.getId();
                    }
                }
                
                // Guardar el archivo en el directorio seleccionado
                if (directorioId != null) {
                    clasificacion.setDirectorioId(directorioId);
                    Archivo archivoCreado = archivoService.crearArchivoDesdeClasificacion(clasificacion, usuario);
                    
                    resultado.put("archivoGuardado", archivoService.toDto(archivoCreado));
                }
            }
            
            return ResponseEntity.ok(resultado);
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al organizar documento: " + e.getMessage()));
        }
    }
}