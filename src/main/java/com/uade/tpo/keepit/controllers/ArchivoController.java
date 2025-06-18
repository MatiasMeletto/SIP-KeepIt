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

                return ResponseEntity.ok()
                        .headers(headers)
                        .body(arch.getContenido());
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
     * y sincroniza los directorios según la estructura recomendada
     */
    @PostMapping("/organizar")
    public ResponseEntity<?> organizarDocumento(@RequestBody ClasificacionCompletaDTO clasificacion, 
                                              @RequestParam(required = false, defaultValue = "false") Boolean guardar,
                                              Authentication authentication) {
        try {
            Usuario usuario = (Usuario) authentication.getPrincipal();
            
            if (clasificacion.getContenidoArchivo() == null || clasificacion.getNombre() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Faltan datos del archivo"));
            }
            
            // Sugerir organización y sincronizar directorios
            Map<String, Object> resultado = openAIClasificadorService.sugerirOrganizacion(clasificacion, usuario);
            
            // Si se solicitó guardar el archivo, hacerlo en el directorio adecuado
            if (guardar) {
                // Determinar el directorio más adecuado según la respuesta
                // Obtener el ID del directorio donde se debería guardar el archivo
                Long directorioId = encontrarDirectorioParaArchivo(resultado, clasificacion.getNombre());
                
                if (directorioId != null) {
                    // Guardar el archivo en el directorio seleccionado
                    clasificacion.setDirectorioId(directorioId);
                    Archivo archivoCreado = archivoService.crearArchivoDesdeClasificacion(clasificacion, usuario);
                    
                    // Agregar información del archivo guardado a la respuesta
                    resultado.put("archivoGuardado", archivoService.toDto(archivoCreado));
                }
            }
            
            return ResponseEntity.ok(resultado);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al organizar documento: " + e.getMessage()));
        }
    }
    
    /**
     * ENDPOINT 3: Guarda directamente un archivo clasificado en un directorio específico
     */
    @PostMapping("/guardar")
    public ResponseEntity<?> guardarArchivo(@RequestBody ClasificacionCompletaDTO clasificacion, 
                                          @RequestParam Long directorioId,
                                          Authentication authentication) {
        try {
            Usuario usuario = (Usuario) authentication.getPrincipal();
            
            if (clasificacion.getContenidoArchivo() == null || clasificacion.getNombre() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Faltan datos del archivo"));
            }
            
            // Verificar que el directorio existe y pertenece al usuario
            Optional<Directorio> directorio = directorioService.obtenerPorId(directorioId);
            if (directorio.isEmpty() || (directorio.get().getUsuario() != null && 
                                       !directorio.get().getUsuario().getId().equals(usuario.getId()))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Directorio no válido o sin acceso"));
            }
            
            // Guardar el archivo en el directorio especificado
            clasificacion.setDirectorioId(directorioId);
            Archivo archivoCreado = archivoService.crearArchivoDesdeClasificacion(clasificacion, usuario);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(archivoService.toDto(archivoCreado));
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al guardar archivo: " + e.getMessage()));
        }
    }
    
    /**
     * Método auxiliar para encontrar el directorio donde debería guardarse un archivo
     * según la respuesta del organizador
     */
    private Long encontrarDirectorioParaArchivo(Map<String, Object> resultado, String nombreArchivo) {
        try {
            // Obtener el mapa de directorios de la respuesta
            Map<String, Object> directorios = (Map<String, Object>) resultado.get("directorios");
            if (directorios == null || directorios.isEmpty()) {
                return null;
            }
            
            // Buscar el directorio que contiene el archivo en su lista
            for (Map.Entry<String, Object> entry : directorios.entrySet()) {
                String dirId = entry.getKey();
                Map<String, Object> dirInfo = (Map<String, Object>) entry.getValue();
                
                // Verificar la lista de archivos
                List<String> archivos = (List<String>) dirInfo.get("archivos");
                if (archivos != null && archivos.contains(nombreArchivo)) {
                    // Encontramos el directorio
                    try {
                        return Long.parseLong(dirId);
                    } catch (NumberFormatException e) {
                        // Si el ID no es numérico, buscar en los directorios actualizados
                        List<Map<String, Object>> actualizados = 
                            (List<Map<String, Object>>) resultado.get("directoriosActualizados");
                            
                        if (actualizados != null) {
                            for (Map<String, Object> dir : actualizados) {
                                if (dir.get("id").toString().equals(dirId)) {
                                    return Long.parseLong(dir.get("id").toString());
                                }
                            }
                        }
                    }
                }
            }
            
            // Si no encontramos un directorio específico, devolver el primer directorio actualizado
            List<Map<String, Object>> actualizados = 
                (List<Map<String, Object>>) resultado.get("directoriosActualizados");
                
            if (actualizados != null && !actualizados.isEmpty()) {
                return Long.parseLong(actualizados.get(0).get("id").toString());
            }
            
            // Si no hay directorios actualizados, intentar con el primer directorio de la respuesta
            if (!directorios.isEmpty()) {
                String primerDirId = directorios.keySet().iterator().next();
                return Long.parseLong(primerDirId);
            }
            
        } catch (Exception e) {
            System.err.println("Error buscando directorio para archivo: " + e.getMessage());
        }
        
        return null;
    }
}