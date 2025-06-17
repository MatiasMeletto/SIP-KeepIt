package com.uade.tpo.keepit.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ContentDisposition;

import com.uade.tpo.keepit.DTO.ArchivoDTO;
import com.uade.tpo.keepit.entities.Archivo;
import com.uade.tpo.keepit.entities.Comprobante;
import com.uade.tpo.keepit.entities.Directorio;
import com.uade.tpo.keepit.entities.Documento;
import com.uade.tpo.keepit.entities.Usuario;
import com.uade.tpo.keepit.enums.Categoria;
import com.uade.tpo.keepit.exceptions.ArchivoDuplicadoException;
import com.uade.tpo.keepit.model.ClasificacionResult;
import com.uade.tpo.keepit.model.ExtraccionAtributos;
import com.uade.tpo.keepit.service.ArchivoClasificadorService;
import com.uade.tpo.keepit.service.ArchivoService;
import com.uade.tpo.keepit.service.DirectorioService;
import com.uade.tpo.keepit.service.UsuarioService;

import java.security.Principal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/archivo")
public class ArchivoController {
    @Autowired
    private ArchivoService archivoService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ArchivoClasificadorService archivoClasificadorService;
    
    @Autowired
    private DirectorioService directorioService; // Agregado: faltaba esta inyección

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
                .filename(archivo.getNombre())
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
            @RequestParam(required = false) String emisor,
            @RequestParam(required = false) String fecha,
            @RequestParam(required = false) Double monto,
            @RequestParam(required = false) Long directorioId,
            Principal principal) {

        try {
            Usuario usuario = usuarioService.findByEmail(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
                    
            // Analizar con IA
            ClasificacionResult resultado = archivoClasificadorService.clasificarArchivo(file);
            ExtraccionAtributos atributos = resultado.getAtributos();
            Directorio directorio = resultado.getDirectorio();
            
            // Sobreescribir con valores proporcionados manualmente si existen
            if (emisor != null && !emisor.isEmpty()) {
                atributos.setEmisor(emisor);
            }
            
            if (fecha != null && !fecha.isEmpty()) {
                atributos.setFecha(fecha);
            }
            
            if (monto != null) {
                atributos.setMonto(monto);
            }
            
            if (directorioId != null) {
                directorio = directorioService.obtenerPorId(directorioId)
                    .orElse(resultado.getDirectorio());
            }
            
            // Crear archivo según el tipo detectado
            Archivo archivo;
            String tipoArchivo = file.getContentType();
            String nombre = file.getOriginalFilename();
            Long tamanioBytes = file.getSize();
            byte[] contenido = file.getBytes();
            Date fechaHoy = new Date();
            
            if (atributos.getMonto() != null) {
                // Crear como comprobante si tiene monto
                Comprobante comprobante = new Comprobante();
                comprobante.setMonto(atributos.getMonto());
                comprobante.setTipoCategoria(Categoria.OTROS); // Corregido: tipoCategoria en lugar de categoria
                
                // No existe setRazonSocialEmisor en Comprobante según la entidad
                // Se podría usar otro campo o agregar ese campo a la entidad
                
                // Fecha de creación (hoy)
                comprobante.setFechaCreacion(new java.sql.Date(fechaHoy.getTime()));
                
                archivo = comprobante;
            } else {
                // Crear como documento
                Documento documento = new Documento();
                java.sql.Date fechaSQL = new java.sql.Date(fechaHoy.getTime());
                java.sql.Date fechaVencimiento = new java.sql.Date(fechaHoy.getTime() + (365 * 24 * 60 * 60 * 1000L));
                documento.setFechaSubida(fechaSQL);
                documento.setFechaVencimiento(fechaVencimiento);
                
                archivo = documento;
            }
            
            // Establecer propiedades comunes
            archivo.setNombre(nombre);
            archivo.setTipoExtension(tipoArchivo);
            archivo.setTamanioBytes(tamanioBytes);
            archivo.setContenido(contenido);
            archivo.setUsuario(usuario);
            archivo.setDirectorio(directorio);
            archivo.setCarpeta(directorio.getRutaCompleta());
            
            Archivo saved = archivoService.saveArchivo(archivo);
            
            // Si hay campos faltantes, devolver 202 Accepted con los campos a completar
            if (!resultado.getCamposFaltantes().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("archivoId", saved.getId());
                response.put("camposFaltantes", resultado.getCamposFaltantes());
                response.put("directorioId", directorio.getId());
                response.put("directorioNombre", directorio.getNombre());
                response.put("atributosExtraidos", atributos);
                
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            }
            
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("Error al guardar el archivo: " + e.getMessage());
        }
    }

    // Este método requiere actualizar la interfaz ArchivoClasificadorService
    // para agregar el método analizarArchivo
    /*
    @PostMapping("/preview")
    public ResponseEntity<ClasificacionResult> previewClasificacion(
            @RequestParam("file") MultipartFile file) {
        try {
            // Usar clasificarArchivo en lugar de analizarArchivo
            ClasificacionResult resultado = archivoClasificadorService.clasificarArchivo(file);
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    */

    // Este método también necesita usar clasificarArchivo
    /*
    @PostMapping("/{archivoId}/reclasificar")
    public ResponseEntity<ArchivoDTO> reclasificarArchivo(@PathVariable Long archivoId) {
        try {
            Optional<Archivo> archivoOpt = archivoService.getArchivoById(archivoId);
            if (archivoOpt.isPresent()) {
                // Este método no existe, habría que adaptarlo
                // Se necesita una versión de clasificarArchivo que acepte byte[] y contentType
                // en lugar de MultipartFile
                Archivo archivo = archivoOpt.get();
                return ResponseEntity.ok(archivoService.toDto(archivo));
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    */

    @PutMapping("/{archivoId}/completar")
    public ResponseEntity<Object> completarArchivo(
            @PathVariable Long archivoId,
            @RequestBody Map<String, Object> datosAdicionales,
            Principal principal) {
        
        try {
            Optional<Archivo> optArchivo = archivoService.getArchivoById(archivoId);
            if (!optArchivo.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            
            Archivo archivo = optArchivo.get();
            
            // Verificar que el usuario es dueño del archivo
            String emailUsuario = principal.getName();
            if (!archivo.getUsuario().getEmail().equals(emailUsuario)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No autorizado");
            }
            
            // Actualizar directorio si se proporcionó
            if (datosAdicionales.containsKey("directorioId")) {
                Long directorioId = Long.valueOf(datosAdicionales.get("directorioId").toString());
                Optional<Directorio> directorio = directorioService.obtenerPorId(directorioId);
                if (directorio.isPresent()) {
                    archivo.setDirectorio(directorio.get());
                    archivo.setCarpeta(directorio.get().getRutaCompleta());
                }
            }
            
            // Actualizar campos específicos según el tipo de archivo
            if (archivo instanceof Comprobante) {
                Comprobante comprobante = (Comprobante) archivo;
                
                if (datosAdicionales.containsKey("monto")) {
                    comprobante.setMonto(Double.valueOf(datosAdicionales.get("monto").toString()));
                }
                
                // No existe razonSocialEmisor en Comprobante según la entidad
                
                if (datosAdicionales.containsKey("tipoCategoria")) {  // Corregido: tipoCategoria en lugar de categoria
                    comprobante.setTipoCategoria(Categoria.valueOf(datosAdicionales.get("tipoCategoria").toString()));
                }
            } else if (archivo instanceof Documento) {
                Documento documento = (Documento) archivo;
                
                if (datosAdicionales.containsKey("fechaVencimiento")) {
                    String fechaStr = datosAdicionales.get("fechaVencimiento").toString();
                    try {
                        java.sql.Date fechaVencimiento = java.sql.Date.valueOf(fechaStr); // formato YYYY-MM-DD
                        documento.setFechaVencimiento(fechaVencimiento);
                    } catch (Exception e) {
                        return ResponseEntity.badRequest().body("Formato de fecha inválido. Use YYYY-MM-DD");
                    }
                }
            }
            
            Archivo updated = archivoService.saveArchivo(archivo);
            return ResponseEntity.ok(updated);
            
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("Error al actualizar los datos: " + e.getMessage());
        }
    }
}
