package com.uade.tpo.keepit.service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.uade.tpo.keepit.DTO.ArchivoDTO;
import com.uade.tpo.keepit.DTO.ClasificacionCompletaDTO;
import com.uade.tpo.keepit.entities.Archivo;
import com.uade.tpo.keepit.entities.Comprobante;
import com.uade.tpo.keepit.entities.Directorio;
import com.uade.tpo.keepit.entities.Documento;
import com.uade.tpo.keepit.entities.FacturaComercial;
import com.uade.tpo.keepit.entities.Usuario;
import com.uade.tpo.keepit.exceptions.ArchivoDuplicadoException;
import com.uade.tpo.keepit.repository.ArchivoRepository;
import com.uade.tpo.keepit.repository.DirectorioRepository;
import com.uade.tpo.keepit.repository.DocumentoRepository;
import com.uade.tpo.keepit.repository.FacturaComercialRepository;

@Service
public class ArchivoServiceImpl implements ArchivoService {

    @Autowired
    private ArchivoRepository archivoRepository;

    @Autowired
    private DirectorioRepository directorioRepository;

    @Autowired
    private DocumentoRepository documentoRepository;
    
    @Autowired
    private FacturaComercialRepository facturaComercialRepository;

    @Override
    public List<ArchivoDTO> getArchivos() {
        return archivoRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Archivo> getArchivoById(Long archivoId) {
        return archivoRepository.findById(archivoId);
    }

    @Override
    public Archivo createArchivo(MultipartFile file, Usuario user) throws ArchivoDuplicadoException {
        // Método legacy - mantener para compatibilidad
        try {
            Documento documento = new Documento();
            documento.setNombre(file.getOriginalFilename());
            documento.setTipoExtension(file.getContentType());
            documento.setTamanioBytes(file.getSize());
            documento.setContenido(java.util.Base64.getEncoder().encodeToString(file.getBytes()));
            documento.setUsuario(user);
            documento.setEliminado(false);
            // fechaSubida se establece automáticamente con @CreationTimestamp

            return archivoRepository.save(documento);
        } catch (Exception e) {
            throw new RuntimeException("Error al crear archivo", e);
        }
    }

    @Override
    public List<ArchivoDTO> obtenerArchivosPorUsuario(Usuario usuario) {
        return archivoRepository.findByUsuarioAndEliminadoFalse(usuario).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Archivo> obtenerArchivoByIdYUsuario(Long archivoId, Usuario usuario) {
        return archivoRepository.findByIdAndUsuarioAndEliminadoFalse(archivoId, usuario);
    }

    @Override
    public Archivo crearArchivoDesdeClasificacion(ClasificacionCompletaDTO clasificacion, Usuario usuario) {
        try {
            // Obtener directorio
            Directorio directorio = null;
            if (clasificacion.getDirectorioId() != null) {
                directorio = directorioRepository.findById(clasificacion.getDirectorioId()).orElse(null);
            }

            // Crear archivo según el tipo detectado
            Archivo archivo;

            switch (clasificacion.getTipoArchivoDetectado() != null ? clasificacion.getTipoArchivoDetectado()
                    : "DOCUMENTO") {
                case "COMPROBANTE":
                    Comprobante comprobante = new Comprobante();
                    comprobante.setMonto(clasificacion.getMonto());
                    archivo = comprobante;
                    break;

                case "FACTURA":
                    FacturaComercial factura = new FacturaComercial();
                    factura.setNumeroFactura(clasificacion.getNumeroFactura());
                    factura.setFechaEmision(clasificacion.getFechaEmision());
                    factura.setMontoTotal(clasificacion.getMonto());
                    factura.setEmisor(clasificacion.getEmisor());
                    factura.setTipoFactura(clasificacion.getTipoFactura());
                    factura.setFechaVencimiento(clasificacion.getFechaVencimiento());
                    archivo = factura;
                    break;

                default:
                    Documento documento = new Documento();
                    documento.setFechaVencimiento(clasificacion.getFechaVencimiento());
                    archivo = documento;
                    break;
            }

            // Propiedades comunes
            // Usar el nombre recomendado por la IA si está disponible, sino usar el original
            String nombreFinal = (clasificacion.getNombreRecomendado() != null && 
                                 !clasificacion.getNombreRecomendado().trim().isEmpty()) 
                                 ? clasificacion.getNombreRecomendado() 
                                 : clasificacion.getNombre();
            
            // Log para debugging
            if (clasificacion.getNombreRecomendado() != null && !clasificacion.getNombreRecomendado().trim().isEmpty()) {
                System.out.println("=== USANDO NOMBRE RECOMENDADO POR IA ===");
                System.out.println("Nombre original: " + clasificacion.getNombre());
                System.out.println("Nombre recomendado: " + clasificacion.getNombreRecomendado());
                System.out.println("==========================================");
            }
            
            archivo.setNombre(nombreFinal);
            archivo.setTipoExtension(clasificacion.getTipoExtension());
            archivo.setUsuario(usuario);
            archivo.setDirectorio(directorio);
            archivo.setEliminado(false);
            // fechaSubida se establece automáticamente con @CreationTimestamp

            // Campos del titular
            archivo.setNombreTitular(clasificacion.getNombreTitular());
            archivo.setDocumentoIdentidadTitular(clasificacion.getDocumentoIdentidadTitular());

            // Contenido del archivo con manejo correcto de Base64
            if (clasificacion.getContenidoArchivo() != null) {
                // No decodifiques, solo guarda el string
                archivo.setContenido(clasificacion.getContenidoArchivo());
                archivo.setTamanioBytes((long) clasificacion.getContenidoArchivo().length());
            }

            return archivoRepository.save(archivo);

        } catch (Exception e) {
            throw new RuntimeException("Error al crear archivo desde clasificación", e);
        }
    }

    @Override
    public ArchivoDTO toDto(Archivo archivo) {
        ArchivoDTO dto = new ArchivoDTO();
        dto.setId(archivo.getId());
        dto.setNombre(archivo.getNombre());
        dto.setTipoExtension(archivo.getTipoExtension());
        dto.setTamanioBytes(archivo.getTamanioBytes());
        dto.setEliminado(archivo.getEliminado());
        dto.setNombreTitular(archivo.getNombreTitular());
        dto.setDocumentoIdentidadTitular(archivo.getDocumentoIdentidadTitular());
        dto.setEstado(archivo.getEstado());

        // Mapear fechaSubida de LocalDateTime a java.sql.Date
        if (archivo.getFechaSubida() != null) {
            dto.setFechaSubida(new Date(archivo.getFechaSubida()
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
        }

        // Información del directorio
        if (archivo.getDirectorio() != null) {
            dto.setDirectorioId(archivo.getDirectorio().getId());
            dto.setDirectorioNombre(archivo.getDirectorio().getNombre());
        }

        // Campos específicos según el tipo
        if (archivo instanceof Comprobante) {
            Comprobante comp = (Comprobante) archivo;
            dto.setTipoArchivo("COMPROBANTE");
            dto.setMonto(comp.getMonto());
        } else if (archivo instanceof Documento) {
            Documento doc = (Documento) archivo;
            dto.setTipoArchivo("DOCUMENTO");
            dto.setFechaVencimiento(doc.getFechaVencimiento());
        } else if (archivo instanceof FacturaComercial) {
            FacturaComercial factura = (FacturaComercial) archivo;
            dto.setTipoArchivo("FACTURA");
            dto.setMonto(factura.getMontoTotal());
            dto.setFechaVencimiento(factura.getFechaVencimiento());
            dto.setNumeroFactura(factura.getNumeroFactura());
            dto.setFechaEmision(factura.getFechaEmision());
            dto.setEmisor(factura.getEmisor());
        }

        return dto;
    }

    @Override
    public Archivo saveArchivo(Archivo archivo) {
        return archivoRepository.save(archivo);
    }

    @Override
    public Void eliminarArchivo(Archivo archivo) {
        // Guardar referencia al directorio antes de eliminar el archivo
        Directorio directorio = archivo.getDirectorio();
        
        // Marcar el archivo como eliminado
        archivo.setEliminado(true);
        archivoRepository.save(archivo);
        
        // Si el archivo pertenecía a un directorio, verificar si quedó vacío
        if (directorio != null) {
            verificarYEliminarDirectorioVacio(directorio);
        }
        
        return null;
    }

    @Override
    public ArchivoDTO actualizarArchivo(Archivo archivo) {
        Archivo actualizado = archivoRepository.save(archivo);
        return toDto(actualizado);
    }

    @Override
    public List<ArchivoDTO> getArchivosConVencimientosCercanos() {
        LocalDate hoy = LocalDate.now();
        LocalDate fechaLimite = hoy.plusDays(7);
        
        List<Documento> documentos = documentoRepository.findByFechaVencimientoBetween(hoy, fechaLimite);
        List<FacturaComercial> facturas = facturaComercialRepository.findFacturasConVencimientoCercano(hoy, fechaLimite);
        
        System.out.println("=== ARCHIVOS CON VENCIMIENTOS CERCANOS ===");
        System.out.println("Rango de fechas: " + hoy + " a " + fechaLimite);
        System.out.println("Cantidad de documentos encontrados: " + documentos.size());
        System.out.println("Cantidad de facturas encontradas: " + facturas.size());
        System.out.println("==========================================");
        
        return Stream.concat(documentos.stream(), facturas.stream())
                .map(this::toDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Obtiene archivos con vencimientos cercanos para un usuario específico
     */
    public List<ArchivoDTO> getArchivosConVencimientosCercanosPorUsuario(Usuario usuario) {
        LocalDate hoy = LocalDate.now();
        LocalDate fechaLimite = hoy.plusDays(7);
        
        // Usar métodos del repositorio que filtran directamente por usuario para mejor performance
        List<Documento> documentos = documentoRepository.findByFechaVencimientoBetweenAndUsuario(hoy, fechaLimite, usuario);
        List<FacturaComercial> facturas = facturaComercialRepository.findFacturasConVencimientoCercanoPorUsuario(hoy, fechaLimite, usuario);
        
        System.out.println("=== ARCHIVOS CON VENCIMIENTOS CERCANOS PARA USUARIO: " + usuario.getUsername() + " ===");
        System.out.println("Rango de fechas: " + hoy + " a " + fechaLimite);
        System.out.println("Cantidad de documentos encontrados: " + documentos.size());
        System.out.println("Cantidad de facturas encontradas: " + facturas.size());
        System.out.println("==========================================");
        
        return Stream.concat(documentos.stream(), facturas.stream())
                .map(this::toDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Obtiene archivos que vencen hoy para un usuario específico
     */
    public List<ArchivoDTO> getArchivosQueVencenHoyPorUsuario(Usuario usuario) {
        List<Documento> documentos = documentoRepository.findDocumentosQueVencenHoyPorUsuario(usuario);
        List<FacturaComercial> facturas = facturaComercialRepository.findFacturasQueVencenHoyPorUsuario(usuario);
        
        System.out.println("=== ARCHIVOS QUE VENCEN HOY PARA USUARIO: " + usuario.getUsername() + " ===");
        System.out.println("Cantidad de documentos que vencen hoy: " + documentos.size());
        System.out.println("Cantidad de facturas que vencen hoy: " + facturas.size());
        System.out.println("==========================================");
        
        return Stream.concat(documentos.stream(), facturas.stream())
                .map(this::toDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Obtiene archivos vencidos para un usuario específico
     */
    public List<ArchivoDTO> getArchivosVencidosPorUsuario(Usuario usuario) {
        List<Documento> documentos = documentoRepository.findDocumentosVencidosPorUsuario(usuario);
        List<FacturaComercial> facturas = facturaComercialRepository.findFacturasVencidasPorUsuario(usuario);
        
        System.out.println("=== ARCHIVOS VENCIDOS PARA USUARIO: " + usuario.getUsername() + " ===");
        System.out.println("Cantidad de documentos vencidos: " + documentos.size());
        System.out.println("Cantidad de facturas vencidas: " + facturas.size());
        System.out.println("==========================================");
        
        return Stream.concat(documentos.stream(), facturas.stream())
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public Long contarArchivosPorUsuario(Usuario usuario) {
        return archivoRepository.countByUsuarioAndEliminadoFalse(usuario);
    }
    
    /**
     * Verifica si un directorio está vacío (sin archivos no eliminados y sin subdirectorios no eliminados)
     * y lo marca como eliminado si es el caso. Aplica recursivamente hacia arriba en la jerarquía.
     */
    private void verificarYEliminarDirectorioVacio(Directorio directorio) {
        if (directorio == null || directorio.getEliminado()) {
            return;
        }
        
        // Contar archivos no eliminados en el directorio
        Long archivosNoEliminados = archivoRepository.countByDirectorioAndEliminadoFalse(directorio);
        
        // Contar subdirectorios no eliminados
        Long subdirectoriosNoEliminados = directorioRepository.countByPadreAndEliminadoFalse(directorio);
        
        // Si no hay archivos no eliminados y no hay subdirectorios no eliminados, marcar como eliminado
        if (archivosNoEliminados == 0 && subdirectoriosNoEliminados == 0) {
            Directorio padre = directorio.getPadre();
            
            // Marcar el directorio como eliminado (soft delete)
            directorio.setEliminado(true);
            directorioRepository.save(directorio);
            
            // Verificar recursivamente el directorio padre
            verificarYEliminarDirectorioVacio(padre);
        }
    }
}
