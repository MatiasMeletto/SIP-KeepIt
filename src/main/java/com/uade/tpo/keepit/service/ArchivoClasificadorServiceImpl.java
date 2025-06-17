package com.uade.tpo.keepit.service;

import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import com.uade.tpo.keepit.entities.Directorio;
import com.uade.tpo.keepit.model.ClasificacionResult;
import com.uade.tpo.keepit.model.ExtraccionAtributos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ArchivoClasificadorServiceImpl implements ArchivoClasificadorService {

    @Autowired
    private DirectorioService directorioService;
    
    @Override
    public ClasificacionResult clasificarArchivo(MultipartFile file) {
        String textoExtraido = "";
        boolean esFactura = false;
        boolean esRecibo = false;
        boolean esBancario = false;
        boolean esImpositivo = false;
        
        ExtraccionAtributos atributos = new ExtraccionAtributos();
        
        try {
            System.out.println("=== Intentando IA de Google Cloud ===");
            System.out.println("=== DEBUG: Iniciando análisis con IA ===");
            System.out.println("Project ID: sip-keepit");
            System.out.println("Tamaño archivo: " + file.getSize() + " bytes");
            
            // Configuración de Google Cloud Vision
            ImageAnnotatorClient vision = ImageAnnotatorClient.create();
            ByteString imgBytes = ByteString.copyFrom(file.getBytes());
            Image img = Image.newBuilder().setContent(imgBytes).build();
            Feature feature = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build();
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                .addFeatures(feature)
                .setImage(img)
                .build();
            
            System.out.println("=== DEBUG: Cliente Vision creado exitosamente ===");
            
            AnnotateImageResponse response = vision.batchAnnotateImages(
                Collections.singletonList(request)).getResponses(0);
            
            if (response.hasError()) {
                System.err.println("Error: " + response.getError().getMessage());
                return fallbackClasificacion(atributos);
            }
            
            TextAnnotation text = response.getFullTextAnnotation();
            if (text != null && text.getText() != null && !text.getText().isEmpty()) {
                textoExtraido = text.getText();
                System.out.println("=== TEXTO EXTRAÍDO ===\n" + textoExtraido);
                
                // Extraer atributos
                atributos = extraerAtributos(textoExtraido);
                
                // Clasificar según el contenido
                if (textoExtraido.toLowerCase().contains("factura") || 
                    textoExtraido.toLowerCase().contains("cuota") ||
                    textoExtraido.toLowerCase().contains("comprobante de pago")) {
                    esFactura = true;
                }
                
                if (textoExtraido.toLowerCase().contains("recibo") || 
                    textoExtraido.toLowerCase().contains("pago") ||
                    textoExtraido.toLowerCase().contains("cobro")) {
                    esRecibo = true;
                }
                
                if (textoExtraido.toLowerCase().contains("banco") || 
                    textoExtraido.toLowerCase().contains("transferencia") ||
                    textoExtraido.toLowerCase().contains("cuenta") ||
                    textoExtraido.toLowerCase().contains("saldo")) {
                    esBancario = true;
                }
                
                if (textoExtraido.toLowerCase().contains("impuesto") || 
                    textoExtraido.toLowerCase().contains("afip") ||
                    textoExtraido.toLowerCase().contains("arba") ||
                    textoExtraido.toLowerCase().contains("agip") ||
                    textoExtraido.toLowerCase().contains("municipalidad")) {
                    esImpositivo = true;
                }
            } else {
                System.out.println("=== NO SE DETECTÓ TEXTO ===");
                return fallbackClasificacion(atributos);
            }
            
            // Determinar directorio más apropiado basado en el contenido
            Directorio directorioDestino;
            
            if (esFactura) {
                directorioDestino = obtenerDirectorioPorRuta("Documentos/Facturas");
            } else if (esRecibo) {
                directorioDestino = obtenerDirectorioPorRuta("Documentos/Recibos");
            } else if (esBancario) {
                directorioDestino = obtenerDirectorioPorRuta("Comprobantes/Bancarios");
            } else if (esImpositivo) {
                directorioDestino = obtenerDirectorioPorRuta("Comprobantes/Impositivos");
            } else {
                directorioDestino = obtenerDirectorioPorRuta("Documentos/General");
            }
            
            // Verificar si se necesita crear un subdirectorio más específico basado en emisor
            if (atributos.getEmisor() != null && !atributos.getEmisor().isEmpty()) {
                directorioDestino = directorioService.crearOObtenerDirectorio(
                    limpiarNombreDirectorio(atributos.getEmisor()), directorioDestino);
            }
            
            String rutaFinal = directorioDestino.getRutaCompleta();
            System.out.println("=== IA exitosa: " + rutaFinal + " ===");
            
            return new ClasificacionResult(
                true, 
                rutaFinal, 
                textoExtraido, 
                directorioDestino, 
                atributos, 
                determinarCamposFaltantes(atributos));
            
        } catch (Exception e) {
            System.err.println("Error en clasificación con IA: " + e.getMessage());
            e.printStackTrace();
            return fallbackClasificacion(atributos);
        }
    }
    
    // Método para limpiar nombres de directorios
    private String limpiarNombreDirectorio(String nombre) {
        // Eliminar caracteres no válidos para nombres de directorios
        return nombre.replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ\\s]", "")
                    .trim()
                    .replaceAll("\\s+", " ");
    }
    
    private Directorio obtenerDirectorioPorRuta(String ruta) {
        String[] partes = ruta.split("/");
        Directorio actual = null;
        
        for (String parte : partes) {
            if (actual == null) {
                actual = directorioService.crearOObtenerDirectorio(parte, null);
            } else {
                actual = directorioService.crearOObtenerDirectorio(parte, actual);
            }
        }
        
        return actual;
    }
    
    private ClasificacionResult fallbackClasificacion(ExtraccionAtributos atributos) {
        Directorio directorioGeneral = obtenerDirectorioPorRuta("Documentos/General");
        List<String> camposFaltantes = determinarCamposFaltantes(atributos);
        
        return new ClasificacionResult(
            false, 
            directorioGeneral.getRutaCompleta(), 
            "", 
            directorioGeneral, 
            atributos,
            camposFaltantes);
    }
    
    private List<String> determinarCamposFaltantes(ExtraccionAtributos atributos) {
        List<String> faltantes = new ArrayList<>();
        
        if (atributos.getFecha() == null) faltantes.add("fecha");
        if (atributos.getEmisor() == null || atributos.getEmisor().isEmpty()) faltantes.add("emisor");
        if (atributos.getMonto() == null) faltantes.add("monto");
        
        return faltantes;
    }
    
    private ExtraccionAtributos extraerAtributos(String texto) {
        ExtraccionAtributos atributos = new ExtraccionAtributos();
        
        // Extraer fecha
        Pattern patternFecha = Pattern.compile("(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}|\\d{4}[/.-]\\d{1,2}[/.-]\\d{1,2})");
        Matcher matcherFecha = patternFecha.matcher(texto);
        if (matcherFecha.find()) {
            atributos.setFecha(matcherFecha.group());
        }
        
        // Extraer monto - versión mejorada
        Pattern patternMonto = Pattern.compile("(?i)(total|monto|importe|valor|precio)[:\\s]*\\$?\\s*([\\d.,]+)");
        Matcher matcherMonto = patternMonto.matcher(texto);
        if (matcherMonto.find()) {
            String montoStr = matcherMonto.group(2).replaceAll("[^\\d.,]", "");
            try {
                double monto = Double.parseDouble(montoStr.replace(",", "."));
                atributos.setMonto(monto);
            } catch (NumberFormatException e) {
                // Intento alternativo
                Pattern altPattern = Pattern.compile("\\$\\s*([\\d.,]+)");
                Matcher altMatcher = altPattern.matcher(texto);
                if (altMatcher.find()) {
                    try {
                        String altMontoStr = altMatcher.group(1).replaceAll("[^\\d.,]", "");
                        double altMonto = Double.parseDouble(altMontoStr.replace(",", "."));
                        atributos.setMonto(altMonto);
                    } catch (NumberFormatException ex) {
                        // No se pudo convertir
                    }
                }
            }
        }
        
        // Extraer emisor - versión mejorada
        Pattern patternEmisor = Pattern.compile("(?i)(razón social|emisor|proveedor|empresa|nombre)[:\\s]+([A-ZaZ0-9 .áéíóúÁÉÍÓÚñÑ&]+)");
        Matcher matcherEmisor = patternEmisor.matcher(texto);
        if (matcherEmisor.find()) {
            atributos.setEmisor(matcherEmisor.group(2).trim());
        } else {
            // Buscar logo o nombre en la parte superior
            String[] lineas = texto.split("\n");
            if (lineas.length > 0 && lineas[0].trim().length() > 3) {
                atributos.setEmisor(lineas[0].trim());
            }
        }
        
        // Extraer concepto/descripción
        Pattern patternConcepto = Pattern.compile("(?i)(concepto|descripción|detalle)[:\\s]+([A-ZaZ0-9 .áéíóúÁÉÍÓÚñÑ&]+)");
        Matcher matcherConcepto = patternConcepto.matcher(texto);
        if (matcherConcepto.find()) {
            atributos.setConcepto(matcherConcepto.group(2).trim());
        }
        
        return atributos;
    }
}