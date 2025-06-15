package com.uade.tpo.keepit.service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.uade.tpo.keepit.DTO.ArchivoDTO;
import com.uade.tpo.keepit.entities.Archivo;
import com.uade.tpo.keepit.entities.Comprobante;
import com.uade.tpo.keepit.entities.Documento;
import com.uade.tpo.keepit.entities.Usuario;
import com.uade.tpo.keepit.enums.Categoria;
import com.uade.tpo.keepit.exceptions.ArchivoDuplicadoException;
import com.uade.tpo.keepit.model.ClasificacionResult;
import com.uade.tpo.keepit.repository.ArchivoRepository;

@Service
public class ArchivoServiceImpl implements ArchivoService {
    @Autowired
    ArchivoRepository archivoRepository;
    
    @Autowired
    ArchivoClasificadorService clasificadorService;

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
        return createArchivo(file, user, null, null, null, null, null);
    }

    @Override
    public Archivo createArchivo(MultipartFile file, Usuario user, String nombreTitular, 
                                String direccionTitular, String emailTitular, 
                                String telefonoTitular, String documentoIdentidadTitular) 
            throws ArchivoDuplicadoException {
        try {
            // *** AQUÍ ENTRA LA MAGIA DE LA IA ***
            ClasificacionResult clasificacion = clasificadorService.analizarArchivo(
                file.getBytes(), file.getContentType());
            
            Archivo archivo;
            
            // Crear Documento o Comprobante según la IA
            if ("COMPROBANTE".equals(clasificacion.getTipoArchivo())) {
                Categoria categoria = mapearCategoria(clasificacion.getCategoria());
                
                archivo = new Comprobante(file, user, nombreTitular, direccionTitular, 
                                        emailTitular, telefonoTitular, documentoIdentidadTitular,
                                        clasificacion.getFechaCreacion(), 
                                        clasificacion.getMonto(),
                                        categoria);
            } else {
                archivo = new Documento(file, user, nombreTitular, direccionTitular, 
                                       emailTitular, telefonoTitular, documentoIdentidadTitular,
                                       clasificacion.getFechaCreacion(), 
                                       clasificacion.getFechaVencimiento());
            }
            
            // Asignar carpeta automáticamente
            archivo.setCarpeta(clasificacion.getCarpeta());
            
            return archivoRepository.save(archivo);
            
        } catch (IOException e) {
            throw new RuntimeException("Error al procesar el archivo", e);
        }
    }

    private Categoria mapearCategoria(String categoriaIA) {
        if (categoriaIA == null) return Categoria.OTROS;
        
        switch (categoriaIA.toUpperCase()) {
            case "FACTURAS": return Categoria.FACTURAS;
            case "RECIBOS": return Categoria.RECIBOS;
            case "SEGUROS": return Categoria.SEGUROS;
            case "VEHICULO": return Categoria.VEHICULO;
            case "SALUD": return Categoria.SALUD;
            default: return Categoria.OTROS;
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
        dto.setDireccionTitular(archivo.getDireccionTitular());
        dto.setEmailTitular(archivo.getEmailTitular());
        dto.setTelefonoTitular(archivo.getTelefonoTitular());
        dto.setDocumentoIdentidadTitular(archivo.getDocumentoIdentidadTitular());
        dto.setCarpeta(archivo.getCarpeta());
        
        // Campos específicos según el tipo
        if (archivo instanceof Comprobante) {
            Comprobante comp = (Comprobante) archivo;
            dto.setTipoArchivo("COMPROBANTE");
            dto.setMonto(comp.getMonto());
            dto.setCategoria(comp.getTipoCategoria() != null ? comp.getTipoCategoria().toString() : null);
            dto.setFechaCreacion(comp.getFechaCreacion());
        } else if (archivo instanceof Documento) {
            Documento doc = (Documento) archivo;
            dto.setTipoArchivo("DOCUMENTO");
            dto.setFechaSubida(doc.getFechaSubida());
            dto.setFechaVencimiento(doc.getFechaVencimiento());
        }
        
        return dto;
    }
}
