package com.uade.tpo.keepit.service;

import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import com.uade.tpo.keepit.model.ClasificacionResult;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ArchivoClasificadorService {
    
    @Value("${google.cloud.project-id}")
    private String projectId;

    public ClasificacionResult analizarArchivo(byte[] contenido, String tipoMime) throws IOException {
        try (ImageAnnotatorClient vision = ImageAnnotatorClient.create()) {
            
            // Crear imagen para Vision API
            ByteString imgBytes = ByteString.copyFrom(contenido);
            Image img = Image.newBuilder().setContent(imgBytes).build();
            Feature feat = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build();
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feat)
                    .setImage(img)
                    .build();

            // Hacer la petición
            BatchAnnotateImagesResponse response = vision.batchAnnotateImages(
                    List.of(request));
            List<AnnotateImageResponse> responses = response.getResponsesList();

            if (!responses.isEmpty() && responses.get(0).hasFullTextAnnotation()) {
                String textoExtraido = responses.get(0).getFullTextAnnotation().getText();
                
                return clasificarDocumento(textoExtraido);
            }
        }
        
        return new ClasificacionResult("documentos/general", "DOCUMENTO", null, null, null, null);
    }

    private ClasificacionResult clasificarDocumento(String texto) {
        String textoLower = texto.toLowerCase();
        
        // Patrones para reconocer tipos de documentos
        if (contienePalabras(textoLower, "factura", "invoice", "total", "$", "monto")) {
            Double monto = extraerMonto(texto);
            return new ClasificacionResult("comprobantes/facturas", "COMPROBANTE", 
                    monto, null, extraerFecha(texto), "FACTURAS");
        }
        
        if (contienePalabras(textoLower, "seguro", "póliza", "cobertura", "prima")) {
            return new ClasificacionResult("vehiculos/seguros", "DOCUMENTO", 
                    null, extraerFechaVencimiento(texto), extraerFecha(texto), null);
        }
        
        if (contienePalabras(textoLower, "dni", "cedula", "identificación", "documento")) {
            return new ClasificacionResult("documentos/identidad", "DOCUMENTO", 
                    null, null, extraerFecha(texto), null);
        }
        
        if (contienePalabras(textoLower, "contrato", "acuerdo", "términos")) {
            return new ClasificacionResult("documentos/contratos", "DOCUMENTO", 
                    null, extraerFechaVencimiento(texto), extraerFecha(texto), null);
        }
        
        if (contienePalabras(textoLower, "recibo", "ticket", "compra")) {
            Double monto = extraerMonto(texto);
            return new ClasificacionResult("comprobantes/recibos", "COMPROBANTE", 
                    monto, null, extraerFecha(texto), "RECIBOS");
        }
        
        // Default
        return new ClasificacionResult("documentos/general", "DOCUMENTO", 
                null, null, extraerFecha(texto), null);
    }

    private boolean contienePalabras(String texto, String... palabras) {
        for (String palabra : palabras) {
            if (texto.contains(palabra)) {
                return true;
            }
        }
        return false;
    }

    private Double extraerMonto(String texto) {
        Pattern pattern = Pattern.compile("\\$?([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]{2})?)");
        Matcher matcher = pattern.matcher(texto);
        if (matcher.find()) {
            try {
                String montoStr = matcher.group(1).replace(",", ".");
                return Double.parseDouble(montoStr);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private java.sql.Date extraerFecha(String texto) {
        Pattern pattern = Pattern.compile("(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})");
        Matcher matcher = pattern.matcher(texto);
        if (matcher.find()) {
            try {
                // Aquí podrías usar una librería como SimpleDateFormat para parsear
                return new java.sql.Date(System.currentTimeMillis());
            } catch (Exception e) {
                return new java.sql.Date(System.currentTimeMillis());
            }
        }
        return new java.sql.Date(System.currentTimeMillis());
    }

    private java.sql.Date extraerFechaVencimiento(String texto) {
        // Similar lógica pero buscando "vencimiento", "expira", etc.
        return new java.sql.Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);
    }
}