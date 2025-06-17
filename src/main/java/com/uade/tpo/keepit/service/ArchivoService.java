package com.uade.tpo.keepit.service;

import java.util.List;
import java.util.Optional;

import org.springframework.web.multipart.MultipartFile;

import com.uade.tpo.keepit.DTO.ArchivoDTO;
import com.uade.tpo.keepit.entities.Archivo;
import com.uade.tpo.keepit.entities.Usuario;
import com.uade.tpo.keepit.exceptions.ArchivoDuplicadoException;

public interface ArchivoService {
    public List<ArchivoDTO> getArchivos();

    public Optional<Archivo> getArchivoById(Long archivoId);

    public Archivo createArchivo(MultipartFile file, Usuario user)
            throws ArchivoDuplicadoException;

    public Archivo saveArchivo(Archivo archivo);

    public ArchivoDTO toDto(Archivo archivo);
}
