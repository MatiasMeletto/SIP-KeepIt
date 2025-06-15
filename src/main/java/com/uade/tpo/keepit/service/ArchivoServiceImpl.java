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
import com.uade.tpo.keepit.entities.Documento;
import com.uade.tpo.keepit.entities.Usuario;
import com.uade.tpo.keepit.exceptions.ArchivoDuplicadoException;
import com.uade.tpo.keepit.repository.ArchivoRepository;

@Service
public class ArchivoServiceImpl implements ArchivoService {
    @Autowired
    ArchivoRepository archivoRepository;

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
            // Por ahora creamos un Documento genérico
            Documento documento = new Documento(file, user, nombreTitular, direccionTitular, 
                                              emailTitular, telefonoTitular, documentoIdentidadTitular,
                                              new java.sql.Date(System.currentTimeMillis()), 
                                              new java.sql.Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000));
            
            return archivoRepository.save(documento);
        } catch (IOException e) {
            throw new RuntimeException("Error al procesar el archivo", e);
        }
    }

    @Override
    public ArchivoDTO toDto(Archivo archivo) {
        ArchivoDTO dto = new ArchivoDTO();
        dto.setId(archivo.getId());
        dto.setNombre(archivo.getNombreArchivo());
        dto.setTipoExtension(archivo.getTipoExtension());
        dto.setTamanioBytes(archivo.getTamanioBytes());
        dto.setEliminado(archivo.getEliminado());
        dto.setNombreTitular(archivo.getNombreTitular());
        dto.setDireccionTitular(archivo.getDireccionTitular());
        dto.setEmailTitular(archivo.getEmailTitular());
        dto.setTelefonoTitular(archivo.getTelefonoTitular());
        dto.setDocumentoIdentidadTitular(archivo.getDocumentoIdentidadTitular());
        return dto;
    }
}
