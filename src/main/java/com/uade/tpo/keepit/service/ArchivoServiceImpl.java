package com.uade.tpo.keepit.service;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

@Service
public class ArchivoServiceImpl implements ArchivoService {
    
    @Autowired
    private ArchivoRepository archivoRepository;

    @Autowired
    private DirectorioRepository directorioRepository;

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
            documento.setFechaSubida(new java.sql.Date(System.currentTimeMillis()));
            
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
            
            switch (clasificacion.getTipoArchivoDetectado() != null ? clasificacion.getTipoArchivoDetectado() : "DOCUMENTO") {
                case "COMPROBANTE":
                    Comprobante comprobante = new Comprobante();
                    comprobante.setMonto(clasificacion.getMonto());
                    comprobante.setFechaCreacion(new java.sql.Date(System.currentTimeMillis()));
                    archivo = comprobante;
                    break;
                    
                case "FACTURA":
                    FacturaComercial factura = new FacturaComercial();
                    factura.setNumeroFactura(clasificacion.getNumeroFactura());
                    factura.setFechaEmision(clasificacion.getFechaEmision());
                    factura.setMontoTotal(clasificacion.getMonto());
                    factura.setEmisor(clasificacion.getEmisor());
                    archivo = factura;
                    break;
                    
                default:
                    Documento documento = new Documento();
                    documento.setFechaSubida(new java.sql.Date(System.currentTimeMillis()));
                    archivo = documento;
                    break;
            }
            
            // Propiedades comunes
            archivo.setNombre(clasificacion.getNombre());
            archivo.setTipoExtension(clasificacion.getTipoExtension());
            archivo.setUsuario(usuario);
            archivo.setDirectorio(directorio);
            archivo.setEliminado(false);
            
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
            dto.setFechaCreacion(comp.getFechaCreacion());
        } else if (archivo instanceof Documento) {
            Documento doc = (Documento) archivo;
            dto.setTipoArchivo("DOCUMENTO");
            dto.setFechaSubida(doc.getFechaSubida());
            dto.setFechaVencimiento(doc.getFechaVencimiento());
        } else if (archivo instanceof FacturaComercial) {
            FacturaComercial factura = (FacturaComercial) archivo;
            dto.setTipoArchivo("FACTURA");
            dto.setMonto(factura.getMontoTotal());
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
        archivo.setEliminado(true);
        archivoRepository.save(archivo);
        return null;
    }

    @Override
    public ArchivoDTO actualizarArchivo(Archivo archivo) {
        Archivo actualizado = archivoRepository.save(archivo);
        return toDto(actualizado);
    }

    @Override
    public List<ArchivoDTO> getArchivosConVencimientosCercanos() {
        List<Archivo> archivos = archivoRepository.findByFechaVencimientoBetween(
                LocalDate.now(),
                LocalDate.now().plusDays(7)
        );
        return archivos.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
}
