package com.uade.tpo.keepit.service;

import com.uade.tpo.keepit.model.ClasificacionResult;
import org.springframework.web.multipart.MultipartFile;

public interface ArchivoClasificadorService {
    ClasificacionResult clasificarArchivo(MultipartFile file);
}