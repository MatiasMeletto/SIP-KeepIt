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
import com.uade.tpo.keepit.entities.Documento;
import com.uade.tpo.keepit.entities.Usuario;
import com.uade.tpo.keepit.service.ArchivoService;
import com.uade.tpo.keepit.service.OpenAIClasificadorService;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;



@RestController
@RequestMapping("/api/archivos")
public class ArchivoController {

    @Autowired
    private ArchivoService archivoService;

    @Autowired
    private OpenAIClasificadorService openAIClasificadorService;

    // Mantener los endpoints existentes...
    @GetMapping
    public ResponseEntity<List<ArchivoDTO>> obtenerArchivosPorUsuario(Authentication authentication) {
        try {
            Usuario usuario = (Usuario) authentication.getPrincipal();
            List<ArchivoDTO> archivos = archivoService.obtenerArchivosPorUsuario(usuario);
            return ResponseEntity.ok(archivos);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    @GetMapping("/vencimientosCercanos")
    public ResponseEntity<List<ArchivoDTO>> getVencimientosCercanos() {
        // Lógica para obtener archivos con vencimientos cercanos (todos los usuarios - admin)
        List<ArchivoDTO> archivos = archivoService.getArchivosConVencimientosCercanos();
        return ResponseEntity.ok(archivos);
    }
    
    @GetMapping("/misVencimientosCercanos")
    public ResponseEntity<List<ArchivoDTO>> getMisVencimientosCercanos(Authentication authentication) {
        try {
            Usuario usuario = (Usuario) authentication.getPrincipal();
            List<ArchivoDTO> archivos = archivoService.getArchivosConVencimientosCercanosPorUsuario(usuario);
            return ResponseEntity.ok(archivos);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }
    
    @GetMapping("/vencen-hoy")
    public ResponseEntity<List<ArchivoDTO>> getArchivosQueVencenHoy(Authentication authentication) {
        try {
            Usuario usuario = (Usuario) authentication.getPrincipal();
            List<ArchivoDTO> archivos = archivoService.getArchivosQueVencenHoyPorUsuario(usuario);
            return ResponseEntity.ok(archivos);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }
    
    @GetMapping("/vencidos")
    public ResponseEntity<List<ArchivoDTO>> getArchivosVencidos(Authentication authentication) {
        try {
            Usuario usuario = (Usuario) authentication.getPrincipal();
            List<ArchivoDTO> archivos = archivoService.getArchivosVencidosPorUsuario(usuario);
            return ResponseEntity.ok(archivos);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
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

                // Decodificar el contenido Base64 a byte[]
                byte[] contenidoBytes = Base64.getDecoder().decode(arch.getContenido());
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
            //System.out.println("Resultado de reconocimiento: " + resultado);
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
            
            Map<String, Object> resultado;
            
            if (guardar) {
                // FLUJO CORREGIDO: Primero guardar el archivo, después organizarlo
                
                // 1. PRIMERO: Crear y guardar el archivo en la base de datos
                Archivo archivoCreado = archivoService.crearArchivoDesdeClasificacion(clasificacion, usuario);
                System.out.println("Archivo creado con ID: " + archivoCreado.getId() + " - " + archivoCreado.getNombre());
                
                // 2. DESPUÉS: Utilizar el servicio de OpenAI para sugerir la organización
                // MODIFICADO: Ahora pasamos el archivo ya creado
                resultado = openAIClasificadorService.sugerirOrganizacion(clasificacion, usuario, archivoCreado);
                
                // Agregar información del archivo guardado a la respuesta
                resultado.put("archivoGuardado", archivoService.toDto(archivoCreado));
                
            } else {
                // Si no se debe guardar, crear un archivo temporal para la organización
                // (este flujo probablemente no se use, pero lo mantengo para compatibilidad)
                Documento archivoTemporal = new Documento();
                archivoTemporal.setNombre(clasificacion.getNombre());
                archivoTemporal.setUsuario(usuario);
                
                resultado = openAIClasificadorService.sugerirOrganizacion(clasificacion, usuario, archivoTemporal);
            }
            
            return ResponseEntity.ok(resultado);
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al organizar documento: " + e.getMessage()));
        }
    }
    @PutMapping("/archivos/{id}")
    public ResponseEntity<ArchivoDTO> actualizarArchivo(@PathVariable Long id, @RequestBody ClasificacionCompletaDTO archivoDTO, Authentication authentication) {
        try {
            Usuario usuario = (Usuario) authentication.getPrincipal();
            Optional<Archivo> archivoOpt = archivoService.obtenerArchivoByIdYUsuario(id, usuario);

            // revisar depues que se va a editar
            if (archivoOpt.isPresent()) {
                Archivo archivo = archivoOpt.get();
                archivo.setNombre(archivoDTO.getNombre());
                archivo.setTipoExtension(archivoDTO.getTipoExtension());
                archivo.setTamanioBytes(archivoDTO.getTamanioBytes());
                archivo.setContenido(archivoDTO.getContenidoArchivo());

                ArchivoDTO actualizado = archivoService.actualizarArchivo(archivo);
                return ResponseEntity.ok(actualizado);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    @DeleteMapping("/{archivoId}")
    public ResponseEntity<?> eliminarArchivo(@PathVariable Long archivoId, Authentication authentication) {
        try {
            Usuario usuario = (Usuario) authentication.getPrincipal();
            Archivo archivo = archivoService.obtenerArchivoByIdYUsuario(archivoId, usuario)
                    .orElseThrow(() -> new RuntimeException("Archivo no encontrado"));
            archivoService.eliminarArchivo(archivo);
            return ResponseEntity.ok(Map.of("mensaje", "Archivo eliminado correctamente"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al eliminar archivo: " + e.getMessage()));
        }
    }
}