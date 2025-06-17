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
import com.uade.tpo.keepit.entities.Directorio;
import com.uade.tpo.keepit.entities.Documento;
import com.uade.tpo.keepit.entities.Usuario;
import com.uade.tpo.keepit.enums.Categoria;
import com.uade.tpo.keepit.exceptions.ArchivoDuplicadoException;
import com.uade.tpo.keepit.model.ClasificacionResult;
import com.uade.tpo.keepit.model.ExtraccionAtributos;
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
        try {
            // Usar el nuevo clasificador que implementa directorios
            ClasificacionResult clasificacion = clasificadorService.clasificarArchivo(file);
            
            // Obtener atributos y directorio identificados
            ExtraccionAtributos atributos = clasificacion.getAtributos();
            Directorio directorio = clasificacion.getDirectorio();
            
            // Crear el tipo de archivo según el resultado del análisis
            Archivo archivo;
            
            // Si detectó un monto, es probable que sea un comprobante
            if (atributos.getMonto() != null) {
                Comprobante comprobante = new Comprobante();
                comprobante.setMonto(atributos.getMonto());
                
                // Determinar categoría basada en el texto o carpeta
                Categoria categoria = determinarCategoria(clasificacion.getCarpeta());
                comprobante.setTipoCategoria(categoria);  // Corregido: tipoCategoria en lugar de categoria
                
                // No existe setRazonSocialEmisor en Comprobante según la entidad proporcionada
                // if (atributos.getEmisor() != null) {
                //    comprobante.setRazonSocialEmisor(atributos.getEmisor());
                // }
                
                // Establecer fecha de creación
                comprobante.setFechaCreacion(
                    atributos.getFecha() != null 
                    ? java.sql.Date.valueOf(convertirFormatoFecha(atributos.getFecha()))
                    : new java.sql.Date(System.currentTimeMillis())
                );
                
                archivo = comprobante;
            } else {
                // Si no detectó monto, lo tratamos como documento
                Documento documento = new Documento();
                
                // Fecha de subida (hoy)
                documento.setFechaSubida(new java.sql.Date(System.currentTimeMillis()));
                
                // Fecha de vencimiento (1 año por defecto)
                documento.setFechaVencimiento(
                    atributos.getFecha() != null 
                    ? java.sql.Date.valueOf(convertirFormatoFecha(atributos.getFecha()))
                    : new java.sql.Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)
                );
                
                archivo = documento;
            }
            
            // Establecer propiedades comunes
            archivo.setNombre(file.getOriginalFilename());
            archivo.setTipoExtension(file.getContentType());
            archivo.setTamanioBytes(file.getSize());
            archivo.setContenido(file.getBytes());
            archivo.setUsuario(user);
            archivo.setDirectorio(directorio);
            archivo.setCarpeta(directorio.getRutaCompleta());
            archivo.setEliminado(false);
            
            // Guardar archivo en la BD
            return archivoRepository.save(archivo);
            
        } catch (IOException e) {
            throw new RuntimeException("Error al procesar el archivo", e);
        }
    }

    private String convertirFormatoFecha(String fecha) {
        // Detectar formato de la fecha (dd/mm/yyyy, yyyy-mm-dd, etc.)
        // y convertir a formato yyyy-MM-dd para SQL Date
        
        if (fecha.matches("\\d{2}[/]\\d{2}[/]\\d{4}")) {
            // Formato dd/mm/yyyy
            String[] partes = fecha.split("/");
            return partes[2] + "-" + partes[1] + "-" + partes[0];
        } else if (fecha.matches("\\d{2}[-]\\d{2}[-]\\d{4}")) {
            // Formato dd-mm-yyyy
            String[] partes = fecha.split("-");
            return partes[2] + "-" + partes[1] + "-" + partes[0];
        }
        
        // Asumir que ya está en formato yyyy-MM-dd o similar
        return fecha;
    }

    private Categoria determinarCategoria(String carpeta) {
        if (carpeta == null) return Categoria.OTROS;
        
        carpeta = carpeta.toLowerCase();
        
        if (carpeta.contains("factura")) return Categoria.FACTURAS;
        if (carpeta.contains("recibo")) return Categoria.RECIBOS;
        if (carpeta.contains("seguro")) return Categoria.SEGUROS;
        if (carpeta.contains("vehiculo")) return Categoria.VEHICULO;
        if (carpeta.contains("salud")) return Categoria.SALUD;
        
        return Categoria.OTROS;
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
        
        // Agregar información del directorio si existe
        if (archivo.getDirectorio() != null) {
            dto.setDirectorioId(archivo.getDirectorio().getId());
            dto.setDirectorioNombre(archivo.getDirectorio().getNombre());
        }
        
        // Campos específicos según el tipo
        if (archivo instanceof Comprobante) {
            Comprobante comp = (Comprobante) archivo;
            dto.setTipoArchivo("COMPROBANTE");
            dto.setMonto(comp.getMonto());
            dto.setCategoria(comp.getTipoCategoria() != null ? comp.getTipoCategoria().toString() : null); // Corregido
            dto.setFechaCreacion(comp.getFechaCreacion());
        } else if (archivo instanceof Documento) {
            Documento doc = (Documento) archivo;
            dto.setTipoArchivo("DOCUMENTO");
            dto.setFechaSubida(doc.getFechaSubida());
            dto.setFechaVencimiento(doc.getFechaVencimiento());
        }
        
        return dto;
    }

    @Override
    public Archivo saveArchivo(Archivo archivo) {
        return archivoRepository.save(archivo);
    }
}
