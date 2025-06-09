package com.uade.tpo.keepit.controllers;

import java.io.FileInputStream;
import java.net.URI;
import java.security.Principal;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.uade.tpo.keepit.DTO.ArchivoDTO;
import com.uade.tpo.keepit.entities.Archivo;
import com.uade.tpo.keepit.entities.Usuario;
import com.uade.tpo.keepit.exceptions.ArchivoDuplicadoException;
import com.uade.tpo.keepit.repository.UserRepository;
import com.uade.tpo.keepit.service.ArchivoService;

@RestController
@RequestMapping("/api/archivo")
public class ArchivoController {
    @Autowired
    private ArchivoService archivoService;

    @Autowired
    private UserRepository usuarioRepository;

    private final Storage storage;
    private final String bucketName = "tu-proyecto.appspot.com"; // Reemplazar con tu bucket de Firebase

    public ArchivoController() throws Exception {
        // IMPORTANTE: Reemplazar con la ruta a tu archivo de credenciales de Firebase
        FileInputStream serviceAccount = new FileInputStream("path/to/serviceAccountKey.json");

        System.setProperty("GOOGLE_APPLICATION_CREDENTIALS", "path/to/serviceAccountKey.json");

        storage = StorageOptions.getDefaultInstance().getService();
    }

    @GetMapping
    public ResponseEntity<List<ArchivoDTO>> getArchivos() {
        return ResponseEntity.ok(archivoService.getArchivos());
    }

    @GetMapping("/{archivoId}")
    public ResponseEntity<ArchivoDTO> getArchivoById(@PathVariable Long archivoId) {
        Optional<Archivo> result = archivoService.getArchivoById(archivoId);
        if (result.isPresent()) {
            Archivo archivo = result.get();
            // Genera URL firmada válida por 1 hora
            String downloadUrl = storage.signUrl(
                    BlobInfo.newBuilder(BlobId.of(bucketName, archivo.getId().toString())).build(),
                    1, TimeUnit.HOURS)
                    .toString();

            ArchivoDTO dto = archivoService.toDto(archivo);
            dto.setPath(downloadUrl); // Asumiendo que agregás un campo path en ArchivoDTO
            return ResponseEntity.ok(dto);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Object> createArchivo(
            @RequestParam("file") MultipartFile file,
            Principal principal) throws ArchivoDuplicadoException {

        try {
            Usuario usuario = usuarioRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            // Crear metadata del archivo
            String nombre = file.getOriginalFilename();
            Long tamanioBytes = file.getSize();
            String tipoArchivo = file.getContentType();
            Date fechaSubida = new Date();
            // Fecha vencimiento 30 días después
            Date fechaVencimiento = new Date(fechaSubida.getTime() + (30L * 24 * 60 * 60 * 1000));

            // Guardar metadata en BD
            Archivo result = archivoService.createArchivo(
                    nombre,
                    tamanioBytes,
                    tipoArchivo,
                    new java.sql.Date(fechaSubida.getTime()),
                    new java.sql.Date(fechaVencimiento.getTime()),
                    usuario);

            // Subir archivo a Firebase
            BlobId blobId = BlobId.of(bucketName, result.getId().toString());
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(tipoArchivo)
                    .build();

            storage.createFrom(blobInfo, file.getInputStream());
            return ResponseEntity
                    .created(URI.create("/archivos/" + result.getId()))
                    .body(result);

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("Error al guardar el archivo: " + e.getMessage());
        }
    }
}
