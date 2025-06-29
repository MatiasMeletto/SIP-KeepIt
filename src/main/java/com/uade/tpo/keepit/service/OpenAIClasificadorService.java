package com.uade.tpo.keepit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import com.uade.tpo.keepit.DTO.ClasificacionCompletaDTO;
import com.uade.tpo.keepit.DTO.DirectorioDTO;
import com.uade.tpo.keepit.DTO.OrganizacionDirectoriosDTO;
import com.uade.tpo.keepit.entities.Archivo;
import com.uade.tpo.keepit.entities.Directorio;
import com.uade.tpo.keepit.entities.Usuario;
import com.uade.tpo.keepit.repository.ArchivoRepository;
import com.uade.tpo.keepit.repository.DirectorioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.gax.core.FixedCredentialsProvider;

import java.io.FileInputStream;
import java.io.InputStream;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class OpenAIClasificadorService {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIClasificadorService.class);

    @Value("${openai.api.key}")
    private String apiKey;
    
    @Value("${openai.api.url}")
    private String apiUrl;
    
    @Value("${openai.assistant.recognizer}")
    private String documentRecognizerAssistantId;
    
    @Value("${openai.assistant.organizer}")
    private String documentOrganizerAssistantId;
    
    @Autowired
    private DirectorioRepository directorioRepository;
    
    @Autowired
    private DirectorioService directorioService;
    
    @Autowired
    private ArchivoRepository archivoRepository;
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Método principal que procesa un archivo para reconocer su contenido
     */
    public ClasificacionCompletaDTO reconocerDocumento(MultipartFile file, Usuario usuario) {
        ClasificacionCompletaDTO dto = new ClasificacionCompletaDTO();
        
        try {
            // Inicializar con datos básicos del archivo
            dto.setNombre(file.getOriginalFilename());
            dto.setTipoExtension(file.getContentType());
            dto.setTamanioBytes(file.getSize());
            dto.setContenidoArchivo(Base64.getEncoder().encodeToString(file.getBytes()));
            
            // 1. Extraer texto con OCR
            String textoExtraido = extraerTextoConOCR(file);
            
            // Si no se pudo extraer texto, usar valores por defecto
            if (textoExtraido == null || textoExtraido.trim().isEmpty()) {
                dto.setTipoArchivoDetectado("DOCUMENTO");
                dto.setResumen("Documento sin texto reconocible");
                return dto;
            }
            
            // 2. Llamar al Assistant de Document Recognizer
            try {
                JsonNode respuestaReconocimiento = llamarDocumentRecognizer(textoExtraido, file);
                
                // 3. Completar el DTO con los datos reconocidos
                dto.setTipoArchivoDetectado(getStringValue(respuestaReconocimiento, "tipoArchivoDetectado", "DOCUMENTO"));
                dto.setResumen(getStringValue(respuestaReconocimiento, "resumen", "Documento escaneado"));
                dto.setNombreTitular(getStringValue(respuestaReconocimiento, "nombreTitular", null));
                dto.setEmisor(getStringValue(respuestaReconocimiento, "emisor", null));
                dto.setNumeroFactura(getStringValue(respuestaReconocimiento, "numeroFactura", null));
                
                // Procesar el monto si existe
                if (respuestaReconocimiento.has("monto") && !respuestaReconocimiento.get("monto").isNull()) {
                    try {
                        JsonNode montoNode = respuestaReconocimiento.get("monto");
                        if (montoNode.isNumber()) {
                            dto.setMonto(montoNode.asDouble());
                        } else {
                            String montoStr = montoNode.asText();
                            if (!"null".equalsIgnoreCase(montoStr)) {
                                // Eliminar caracteres no numéricos excepto punto decimal
                                montoStr = montoStr.replaceAll("[^\\d.]", "");
                                if (!montoStr.isEmpty()) {
                                    dto.setMonto(Double.parseDouble(montoStr));
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error al procesar el monto: " + e.getMessage());
                    }
                }
                
                // Procesar la fecha si existe
                String fechaEmision = getStringValue(respuestaReconocimiento, "fechaEmision", null);
                if (fechaEmision != null) {
                    try {
                        // Limpiar formato y convertir a fecha SQL
                        fechaEmision = fechaEmision.replace("_o_null", "");
                        dto.setFechaEmision(java.sql.Date.valueOf(fechaEmision));
                    } catch (Exception e) {
                        System.err.println("Error al procesar la fecha: " + e.getMessage());
                    }
                }
                
            } catch (Exception e) {
                System.err.println("Error al procesar con IA: " + e.getMessage());
                dto.setTipoArchivoDetectado("DOCUMENTO");
                dto.setResumen("No se pudo procesar con IA: " + e.getMessage());
            }
            
        } catch (Exception e) {
            System.err.println("Error al reconocer documento: " + e.getMessage());
            e.printStackTrace();
            dto.setTipoArchivoDetectado("DOCUMENTO");
            dto.setResumen("Error en el procesamiento: " + e.getMessage());
        }
        
        return dto;
    }
    
    /**
     * Método para sugerir organización del documento y sincronizar directorios
     */
    public Map<String, Object> sugerirOrganizacion(ClasificacionCompletaDTO clasificacion, Usuario usuario) {
        try {
            // 1. Obtener lista de directorios del usuario en formato DTO para la IA
            List<DirectorioDTO> estructuraDirectorios = directorioService.obtenerDirectoriosParaIA(usuario);
            
            // 2. Crear el objeto de entrada para la IA según el formato especificado en el prompt
            Map<String, Object> entradaIA = new HashMap<>();
            entradaIA.put("nombre", clasificacion.getNombre());
            entradaIA.put("tipoExtension", clasificacion.getTipoExtension());
            entradaIA.put("resumen", clasificacion.getResumen());
            entradaIA.put("nombreTitular", clasificacion.getNombreTitular());
            entradaIA.put("emisor", clasificacion.getEmisor());
            entradaIA.put("tipoArchivoDetectado", clasificacion.getTipoArchivoDetectado());
            
            if (clasificacion.getFechaEmision() != null) {
                entradaIA.put("fechaEmision", clasificacion.getFechaEmision().toString());
            }
            
            // Agregar los directorios
            entradaIA.put("directorios", estructuraDirectorios);
            
            // 3. Llamar al Assistant de Document Organizer
            JsonNode respuestaJson = llamarDocumentOrganizer(entradaIA);
            
            // 4. Convertir respuesta a DTO
            OrganizacionDirectoriosDTO organizacionDTO = procesarRespuestaOrganizador(respuestaJson);
            
            // 5. Sincronizar estructura de directorios según la respuesta
            Map<String, Object> resultadoFinal = directorioService.sincronizarDirectorios(organizacionDTO, usuario);
            
            return resultadoFinal;
            
        } catch (Exception e) {
            throw new RuntimeException("Error al sugerir organización: " + e.getMessage(), e);
        }
    }

    /**
     * Extrae texto de una imagen o PDF usando Google Cloud Vision
     */
    private String extraerTextoConOCR(MultipartFile file) {
        try {
            System.out.println("=== Extrayendo texto con OCR ===");
            
            // Configurar credenciales explícitamente
            String credentialsPath = "C:\\Users\\matis\\SIP-KeepIt\\src\\main\\resources\\google-cloud-key.json";
            InputStream credentialsStream = new FileInputStream(credentialsPath);
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);
            ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build();
            
            // Crear cliente Vision API con las credenciales explícitas
            ImageAnnotatorClient vision = ImageAnnotatorClient.create(settings);
            
            ByteString imgBytes = ByteString.copyFrom(file.getBytes());
            Image img = Image.newBuilder().setContent(imgBytes).build();
            Feature feature = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build();
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                .addFeatures(feature)
                .setImage(img)
                .build();
            
            // Ejecutar detección de texto
            AnnotateImageResponse response = vision.batchAnnotateImages(
                Collections.singletonList(request)).getResponses(0);
            
            if (response.hasError()) {
                System.err.println("Error OCR: " + response.getError().getMessage());
                return "";
            }
            
            TextAnnotation text = response.getFullTextAnnotation();
            if (text != null && text.getText() != null && !text.getText().isEmpty()) {
                String textoExtraido = text.getText();
                System.out.println("=== TEXTO EXTRAÍDO ===\n" + textoExtraido);
                return textoExtraido;
            }
            
            return "";
            
        } catch (Exception e) {
            System.err.println("Error en OCR: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }
    
    /**
     * Llama al OpenAI Document Recognizer Assistant
     */
    private JsonNode llamarDocumentRecognizer(String textoExtraido, MultipartFile file) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        
        String mensajeContenido = "Analiza el siguiente texto extraído por OCR y clasifica el documento. " + 
                             "Responde con un objeto JSON con los siguientes campos cuando sea posible: " +
                             "tipoArchivoDetectado, resumen, nombreTitular, emisor, numeroFactura, monto, fechaEmision.\n\n" + 
                             textoExtraido + "\n\n" + 
                             "Información del archivo:\n" + 
                             "Nombre: " + file.getOriginalFilename() + "\n" +
                             "Tipo: " + file.getContentType() + "\n" +
                             "Tamaño: " + file.getSize() + " bytes";
    
        // Preparar cuerpo de la solicitud
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", mensajeContenido);
    
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o");
        requestBody.put("messages", Collections.singletonList(message));
        requestBody.put("temperature", 0.1);
        requestBody.put("response_format", Map.of("type", "json_object"));
    
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
    
        try {
            System.out.println("Enviando solicitud a OpenAI API...");
            ResponseEntity<String> response = restTemplate.postForEntity(
                apiUrl,
                request, 
                String.class
            );
            
            // Depuración - muestra la respuesta completa
            System.out.println("Respuesta de OpenAI: " + response.getBody());
            
            if (response.getStatusCode() == HttpStatus.OK) {
                // Extraer el contenido del mensaje de respuesta
                JsonNode responseNode = objectMapper.readTree(response.getBody());
                String responseContent = responseNode.path("choices").path(0).path("message").path("content").asText();
                
                // Intentar parsear la respuesta como JSON
                try {
                    return objectMapper.readTree(responseContent);
                } catch (Exception e) {
                    System.err.println("Error al parsear respuesta como JSON: " + e.getMessage());
                    
                    // Crear una respuesta por defecto
                    ObjectNode defaultNode = objectMapper.createObjectNode();
                    defaultNode.put("tipoArchivoDetectado", "DOCUMENTO");
                    defaultNode.put("resumen", "Documento escaneado sin texto reconocible");
                    
                    return defaultNode;
                }
            } else {
                throw new RuntimeException("Error en la respuesta de OpenAI: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("Error al llamar a OpenAI API: " + e.getMessage());
            e.printStackTrace();
            
            // Devolver un objeto JSON por defecto en caso de error
            ObjectNode defaultNode = objectMapper.createObjectNode();
            defaultNode.put("tipoArchivoDetectado", "DOCUMENTO");
            defaultNode.put("resumen", "No se pudo procesar: " + e.getMessage());
            
            return defaultNode;
        }
    }
    
    /**
     * Llama al OpenAI Document Organizer Assistant
     */
    private JsonNode llamarDocumentOrganizer(Map<String, Object> datos) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        // Agregar el header necesario para Assistants API
        headers.add("OpenAI-Beta", "assistants=v2");
        
        // Convertir el mapa de datos a JSON
        String datosJson = objectMapper.writeValueAsString(datos);
        
        // 1. Crear un thread
        Map<String, Object> threadRequest = new HashMap<>();
        HttpEntity<Map<String, Object>> threadEntity = new HttpEntity<>(threadRequest, headers);
        ResponseEntity<String> threadResponse = restTemplate.postForEntity(
            "https://api.openai.com/v1/threads",
            threadEntity,
            String.class
        );
        String threadId = objectMapper.readTree(threadResponse.getBody()).get("id").asText();
        
        // 2. Añadir mensaje al thread
        Map<String, Object> messageRequest = new HashMap<>();
        messageRequest.put("role", "user");
        messageRequest.put("content", datosJson); // Solo enviamos el JSON, sin instrucciones
        
        HttpEntity<Map<String, Object>> messageEntity = new HttpEntity<>(messageRequest, headers);
        restTemplate.postForEntity(
            "https://api.openai.com/v1/threads/" + threadId + "/messages",
            messageEntity,
            String.class
        );
        
        // 3. Ejecutar el assistant
        Map<String, Object> runRequest = new HashMap<>();
        runRequest.put("assistant_id", documentOrganizerAssistantId);
        
        HttpEntity<Map<String, Object>> runEntity = new HttpEntity<>(runRequest, headers);
        ResponseEntity<String> runResponse = restTemplate.postForEntity(
            "https://api.openai.com/v1/threads/" + threadId + "/runs",
            runEntity,
            String.class
        );
        
        String runId = objectMapper.readTree(runResponse.getBody()).get("id").asText();
        
        // 4. Esperar a que termine el run
        String status = "queued";
        while (!status.equals("completed")) {
            Thread.sleep(1000); // Esperar 1 segundo
            ResponseEntity<String> statusResponse = restTemplate.exchange(
                "https://api.openai.com/v1/threads/" + threadId + "/runs/" + runId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );
            
            status = objectMapper.readTree(statusResponse.getBody()).get("status").asText();
            if (status.equals("failed") || status.equals("cancelled")) {
                throw new RuntimeException("Run failed with status: " + status);
            }
        }
        
        // 5. Obtener mensajes
        ResponseEntity<String> messagesResponse = restTemplate.exchange(
            "https://api.openai.com/v1/threads/" + threadId + "/messages",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class
        );
        
        // Obtener el último mensaje del assistant
        JsonNode messages = objectMapper.readTree(messagesResponse.getBody()).get("data");
        String content = "";
        
        for (JsonNode message : messages) {
            if (message.get("role").asText().equals("assistant")) {
                content = message.get("content").get(0).get("text").get("value").asText();
                break;
            }
        }
        
        // NUEVO: Extraer el JSON del bloque de código
        if (content.startsWith("```")) {
            // Extraer el contenido dentro del bloque de código
            int startIndex = content.indexOf('\n');
            if (startIndex != -1) {
                int endIndex = content.lastIndexOf("```");
                if (endIndex != -1) {
                    content = content.substring(startIndex + 1, endIndex).trim();
                }
            }
        }

        // Para debugging
        System.out.println("CONTENIDO JSON EXTRAÍDO: " + content);
        
        // Ahora parsear el JSON limpio
        return objectMapper.readTree(content);
    }
    
    /**
     * Procesa la respuesta JSON del organizador y la convierte a DTO
     */
    private OrganizacionDirectoriosDTO procesarRespuestaOrganizador(JsonNode respuestaJson) {
        OrganizacionDirectoriosDTO dto = new OrganizacionDirectoriosDTO();
        
        // Extraer explicación
        if (respuestaJson.has("explicacion")) {
            dto.setExplicacion(respuestaJson.get("explicacion").asText());
        } else {
            dto.setExplicacion("No se proporcionó explicación");
        }
        
        // Extraer directorios
        List<DirectorioDTO> directorios = new ArrayList<>();
        
        if (respuestaJson.has("directorios") && respuestaJson.get("directorios").isArray()) {
            JsonNode directoriosNode = respuestaJson.get("directorios");
            
            for (JsonNode dirNode : directoriosNode) {
                DirectorioDTO dirDTO = new DirectorioDTO();
                
                if (dirNode.has("id_directorio")) {
                    dirDTO.setId_directorio(dirNode.get("id_directorio").asText());
                }
                
                if (dirNode.has("nombre")) {
                    dirDTO.setNombre(dirNode.get("nombre").asText());
                }
                
                if (dirNode.has("id_directorio_padre")) {
                    JsonNode padreNode = dirNode.get("id_directorio_padre");
                    dirDTO.setId_directorio_padre(padreNode.isNull() ? null : padreNode.asText());
                }
                
                // Extraer archivos
                List<String> archivos = new ArrayList<>();
                if (dirNode.has("archivos") && dirNode.get("archivos").isArray()) {
                    for (JsonNode archivoNode : dirNode.get("archivos")) {
                        archivos.add(archivoNode.asText());
                    }
                }
                dirDTO.setArchivos(archivos);
                
                directorios.add(dirDTO);
            }
        }
        
        dto.setDirectorios(directorios);
        return dto;
    }
    // Método auxiliar para extraer valores de string de un JSON con valor por defecto
    private String getStringValue(JsonNode node, String fieldName, String defaultValue) {
        if (node != null && node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asText();
        }
        return defaultValue;
    }
}