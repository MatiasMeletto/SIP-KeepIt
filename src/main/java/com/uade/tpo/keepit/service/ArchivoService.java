package com.uade.tpo.keepit.service;

import java.sql.Date;
import java.util.List;
import java.util.Optional;

import com.uade.tpo.keepit.entities.Archivo;
import com.uade.tpo.keepit.exceptions.ArchivoDuplicadoException;

public interface ArchivoService {
    public List<Archivo> getArchivos();

    public Optional<Archivo> getArchivoById(Long archivoId);

    public Archivo createArchivo(String nombre, Long tamanioBytes, String tipoArchivo, Date fechaSubida,
            Date fechaVencimiento)
            throws ArchivoDuplicadoException;
}
