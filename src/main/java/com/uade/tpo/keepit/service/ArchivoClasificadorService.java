package com.uade.tpo.keepit.service;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import com.uade.tpo.keepit.model.ClasificacionResult;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ArchivoClasificadorService {
    
    @Value("${google.cloud.project-id}")
    private String projectId;

    public ClasificacionResult analizarArchivo(byte[] contenido, String tipoMime) throws IOException {
        try {
            System.out.println("=== DEBUG: Iniciando análisis con IA ===");
            System.out.println("Project ID: " + projectId);
            System.out.println("Tamaño archivo: " + contenido.length + " bytes");
            
            // *** CARGAR CREDENCIALES MANUALMENTE ***
            InputStream credentialsStream = getClass().getClassLoader()
                .getResourceAsStream("google-cloud-key.json");
            
            if (credentialsStream == null) {
                throw new IOException("No se encontró el archivo google-cloud-key.json");
            }
            
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);
            
            ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build();
            
            try (ImageAnnotatorClient vision = ImageAnnotatorClient.create(settings)) {
                System.out.println("=== DEBUG: Cliente Vision creado exitosamente ===");
                
                // Resto del código de análisis...
                ByteString imgBytes = ByteString.copyFrom(contenido);
                Image img = Image.newBuilder().setContent(imgBytes).build();
                Feature feat = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build();
                AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feat)
                    .setImage(img)
                    .build();

                BatchAnnotateImagesResponse response = vision.batchAnnotateImages(
                    Arrays.asList(request));
                
                List<AnnotateImageResponse> responses = response.getResponsesList();
                
                if (!responses.isEmpty() && responses.get(0).hasFullTextAnnotation()) {
                    String texto = responses.get(0).getFullTextAnnotation().getText();
                    System.out.println("=== TEXTO DETECTADO: " + texto + " ===");
                    
                    return clasificarPorTexto(texto);
                } else {
                    System.out.println("=== NO SE DETECTÓ TEXTO ===");
                    return new ClasificacionResult("documentos/general", "DOCUMENTO", 
                        null, new java.sql.Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000), 
                        new java.sql.Date(System.currentTimeMillis()), null);
                }
            }
            
        } catch (Exception e) {
            System.out.println("=== ERROR EN IA: " + e.getMessage() + " ===");
            e.printStackTrace();
            throw new IOException("Error en análisis de IA", e);
        }
    }
    
    private ClasificacionResult clasificarPorTexto(String texto) {
        String textoLower = texto.toLowerCase();
        
        if (textoLower.contains("factura") || textoLower.contains("invoice")) {
            // Extraer monto si existe
            Double monto = extraerMonto(texto);
            return new ClasificacionResult("comprobantes/facturas", "COMPROBANTE", 
                monto != null ? monto : 0.0, null, 
                new java.sql.Date(System.currentTimeMillis()), "FACTURAS");
                
        } else if (textoLower.contains("recibo") || textoLower.contains("receipt")) {
            Double monto = extraerMonto(texto);
            return new ClasificacionResult("comprobantes/recibos", "COMPROBANTE", 
                monto != null ? monto : 0.0, null, 
                new java.sql.Date(System.currentTimeMillis()), "RECIBOS");
                
        } else if (textoLower.contains("seguro") || textoLower.contains("póliza")) {
            return new ClasificacionResult("vehiculos/seguros", "DOCUMENTO", 
                null, new java.sql.Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000), 
                new java.sql.Date(System.currentTimeMillis()), null);
                
        } else {
            return new ClasificacionResult("documentos/general", "DOCUMENTO", 
                null, new java.sql.Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000), 
                new java.sql.Date(System.currentTimeMillis()), null);
        }
    }
    
    private Double extraerMonto(String texto) {
        // Buscar patrones como $123.45, $123,45, etc.
        Pattern pattern = Pattern.compile("\\$\\s*([0-9,]+\\.?[0-9]*)");
        Matcher matcher = pattern.matcher(texto);
        
        if (matcher.find()) {
            try {
                String montoStr = matcher.group(1).replace(",", "");
                return Double.parseDouble(montoStr);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}