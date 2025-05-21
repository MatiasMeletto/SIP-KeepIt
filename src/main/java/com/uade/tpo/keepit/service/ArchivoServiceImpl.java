package com.uade.tpo.keepit.service;

import java.sql.Date;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.uade.tpo.keepit.DTO.ArchivoDTO;
import com.uade.tpo.keepit.entities.Archivo;
import com.uade.tpo.keepit.entities.Documento;
import com.uade.tpo.keepit.exceptions.ArchivoDuplicadoException;
import com.uade.tpo.keepit.repository.ArchivoRepository;

@Service
public class ArchivoServiceImpl implements ArchivoService {
    @Autowired
    ArchivoRepository archivoRepository;

    public List<ArchivoDTO> getArchivos() {
        List<Archivo> archivos = archivoRepository.findAll();
        return archivos.stream().map(archivo -> this.toDto(archivo)).toList();
    }

    public Optional<Archivo> getArchivoById(Long archivoId) {
        return archivoRepository.findById(archivoId);
    }

    // Aplicar lógica para delegar al controler luego
    public Archivo createArchivo(String nombre, Long tamanioBytes, String tipoArchivo, Date fechaSubida,
            Date fechaVencimiento)
            throws ArchivoDuplicadoException {
        List<Archivo> archivos = archivoRepository.findAll();
        if (archivos.stream().anyMatch(archivo -> archivo.getNombre().equals(nombre)))
            throw new ArchivoDuplicadoException();

        return archivoRepository.save(new Documento(nombre, tamanioBytes, tipoArchivo, fechaSubida, fechaVencimiento));
    }

    public ArchivoDTO toDto(Archivo archivo) {
        return new ArchivoDTO(
                archivo.getId(),
                archivo.getNombre(),
                archivo.getTipoExtension(),
                archivo.getTamanioBytes(),
                null,
                null);
    }
}
