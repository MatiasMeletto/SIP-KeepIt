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
import com.uade.tpo.keepit.entities.Usuario;
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
import java.util.*;
import java.util.Base64;

@Service
public class OpenAIClasificadorService {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String apiUrl;

    @Value("${openai.assistant.recognizer}")
    private String documentRecognizerAssistantId;

    @Value("${openai.assistant.organizer}")
    private String documentOrganizerAssistantId;

    @Autowired
    private DirectorioService directorioService;

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

            // Verificar si es PDF para procesamiento directo
            String contentType = file.getContentType();
            
            if (contentType != null && contentType.equals("application/pdf")) {
                // Para PDFs, enviar directamente al Assistant sin OCR
                try {
                    System.out.println("=== PROCESANDO PDF ===");
                    System.out.println("Nombre: " + file.getOriginalFilename());
                    System.out.println("Tamaño: " + file.getSize() + " bytes");
                    System.out.println("Content-Type: " + contentType);
                    
                    JsonNode respuestaReconocimiento = llamarDocumentRecognizer("", file);
                    
                    System.out.println("=== RESPUESTA DEL ASSISTANT PARA PDF ===");
                    System.out.println(respuestaReconocimiento.toString());
                    System.out.println("==========================================");
                    
                    // 3. Completar el DTO con los datos reconocidos
                    dto.setTipoArchivoDetectado(
                            getStringValue(respuestaReconocimiento, "tipoArchivoDetectado", "DOCUMENTO"));
                    dto.setResumen(getStringValue(respuestaReconocimiento, "resumen", "PDF procesado"));
                    dto.setNombreTitular(getStringValue(respuestaReconocimiento, "nombreTitular", null));
                    dto.setEmisor(getStringValue(respuestaReconocimiento, "emisor", null));
                    dto.setNumeroFactura(getStringValue(respuestaReconocimiento, "numeroFactura", null));
                    dto.setNombreRecomendado(getStringValue(respuestaReconocimiento, "nombreRecomendado", null));

                    // Procesar el resto de campos como antes
                    procesarCamposAdicionales(respuestaReconocimiento, dto);
                    
                } catch (Exception e) {
                    System.err.println("Error al procesar PDF con IA: " + e.getMessage());
                    e.printStackTrace();
                    dto.setTipoArchivoDetectado("DOCUMENTO");
                    dto.setResumen("No se pudo procesar PDF con IA: " + e.getMessage());
                }
                
                return dto;
            }

            // Para imágenes, usar OCR como antes
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
                dto.setTipoArchivoDetectado(
                        getStringValue(respuestaReconocimiento, "tipoArchivoDetectado", "DOCUMENTO"));
                dto.setResumen(getStringValue(respuestaReconocimiento, "resumen", "Documento escaneado"));
                dto.setNombreTitular(getStringValue(respuestaReconocimiento, "nombreTitular", null));
                dto.setEmisor(getStringValue(respuestaReconocimiento, "emisor", null));
                dto.setNumeroFactura(getStringValue(respuestaReconocimiento, "numeroFactura", null));
                dto.setNombreRecomendado(getStringValue(respuestaReconocimiento, "nombreRecomendado", null));
                dto.setDocumentoIdentidadTitular(
                        getStringValue(respuestaReconocimiento, "documentoIdentidadTitular", null));

                // Procesar campos adicionales
                procesarCamposAdicionales(respuestaReconocimiento, dto);

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
     * MODIFICADO: Ahora recibe el archivo ya creado como parámetro
     */
    public Map<String, Object> sugerirOrganizacion(ClasificacionCompletaDTO clasificacion, Usuario usuario, Archivo archivoCreado) {
        try {
            // 1. Obtener lista de directorios del usuario en formato DTO para la IA
            List<DirectorioDTO> estructuraDirectorios = directorioService.obtenerDirectoriosParaIA(usuario);

            // 2. Crear el objeto de entrada para la IA según el formato especificado en el
            // prompt

            // aca agregar datos que quiero que sirvan para la reorganización
            Map<String, Object> entradaIA = new HashMap<>();
            // Usar el nombre recomendado por la IA si está disponible, sino el nombre original
            String nombreParaOrganizar = clasificacion.getNombreRecomendado() != null && !clasificacion.getNombreRecomendado().trim().isEmpty()
                    ? clasificacion.getNombreRecomendado()
                    : clasificacion.getNombre();
            System.out.println("=== ENVIANDO AL DOCUMENT ORGANIZER ===");
            System.out.println("Nombre original: " + clasificacion.getNombre());
            System.out.println("Nombre recomendado por IA: " + clasificacion.getNombreRecomendado());
            System.out.println("Nombre que se enviará al organizer: " + nombreParaOrganizar);
            entradaIA.put("nombre", nombreParaOrganizar);
            entradaIA.put("tipoExtension", clasificacion.getTipoExtension());
            entradaIA.put("resumen", clasificacion.getResumen());
            entradaIA.put("nombreTitular", clasificacion.getNombreTitular());
            entradaIA.put("emisor", clasificacion.getEmisor());
            entradaIA.put("tipoArchivoDetectado", clasificacion.getTipoArchivoDetectado());
            entradaIA.put("fechaVencimiento",
                    clasificacion.getFechaVencimiento() != null ? clasificacion.getFechaVencimiento().toString()
                            : null);
            entradaIA.put("emailTitular", clasificacion.getEmailTitular());

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
            // MODIFICADO: Ahora pasamos el archivo ya creado
            Map<String, Object> resultadoFinal = directorioService.sincronizarDirectorios(organizacionDTO, usuario, archivoCreado);

            return resultadoFinal;

        } catch (Exception e) {
            throw new RuntimeException("Error al sugerir organización: " + e.getMessage(), e);
        }
    }

    /**
     * Extrae texto de una imagen o PDF usando Google Cloud Vision o Apache PDFBox
     */
    private String extraerTextoConOCR(MultipartFile file) {
        try {
            String contentType = file.getContentType();
            
            // Si es PDF, usar Apache PDFBox
            if (contentType != null && contentType.equals("application/pdf")) {
                return extraerTextoDePDF(file);
            }
            
            // Si es imagen, usar Google Cloud Vision OCR
            return extraerTextoConGoogleVision(file);
            
        } catch (Exception e) {
            System.err.println("Error en extracción de texto: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }
    
    /**
     * Extrae texto de PDFs usando Apache PDFBox
     */
    private String extraerTextoDePDF(MultipartFile file) {
        try {
            // TODO: Implementar PDFBox después de agregar la dependencia
            System.err.println("Extracción de PDF no implementada aún. Necesita dependencia PDFBox.");
            return "PDF procesado - extracción pendiente";
            
        } catch (Exception e) {
            System.err.println("Error al extraer texto del PDF: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }
    
    /**
     * Extrae texto de imágenes usando Google Cloud Vision
     */
    private String extraerTextoConGoogleVision(MultipartFile file) {
        try {
            //System.out.println("=== Extrayendo texto con OCR ===");

            // Configurar credenciales explícitamente
            String credentialsPath = "C:\\Users\\matis\\SIP-KeepIt\\src\\main\\resources\\google-cloud-key.json";
            InputStream credentialsStream = new FileInputStream(credentialsPath);
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);
            ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build();

            // Crear cliente Vision API con las credenciales explícitas
            // Usar try-with-resources para cerrar automáticamente el cliente
            try (ImageAnnotatorClient vision = ImageAnnotatorClient.create(settings)) {
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
                    //System.out.println("=== TEXTO EXTRAÍDO ===\n" + textoExtraido);
                    return textoExtraido;
                }

                return "";
            } // El cliente se cierra automáticamente aquí

        } catch (Exception e) {
            System.err.println("Error en OCR con Google Vision: " + e.getMessage());
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
        // Agregar el header necesario para Assistants API
        headers.add("OpenAI-Beta", "assistants=v2");

        String contentType = file.getContentType();
        String archivoBase64 = Base64.getEncoder().encodeToString(file.getBytes());
        
        // Verificar el tamaño del mensaje para decidir el método
        boolean esPDF = (contentType != null && contentType.equals("application/pdf"));
        boolean archivoMuyGrande = archivoBase64.length() > 200000; // Límite conservador de 200K caracteres
        
        // Para archivos grandes, usar File API. Para pequeños, usar Base64 en mensaje
        boolean usarFileAPI = esPDF && archivoMuyGrande;

        System.out.println("=== ANALIZANDO ARCHIVO ===");
        System.out.println("Archivo: " + file.getOriginalFilename());
        System.out.println("Tamaño: " + file.getSize() + " bytes");
        System.out.println("Content-Type: " + contentType);
        System.out.println("Base64 length: " + archivoBase64.length() + " caracteres");
        System.out.println("¿Usar File API?: " + usarFileAPI);
        System.out.println("==============================");

        // Preparar el contenido del mensaje según el método elegido
        String mensajeContenido = "";
        String fileId = null;
        
        if (usarFileAPI) {
            // Método 1: Usar File API para archivos grandes
            try {
                fileId = subirArchivoAOpenAI(file);
                mensajeContenido = "Por favor, procesa el archivo PDF adjunto (File ID: " + fileId + ") y extrae la información solicitada:\n\n" +
                        "INFORMACIÓN DEL ARCHIVO:\n" +
                        "- Nombre: " + file.getOriginalFilename() + "\n" +
                        "- Tipo: " + file.getContentType() + "\n" +
                        "- Tamaño: " + file.getSize() + " bytes";
                
                System.out.println("=== USANDO FILE API ===");
                System.out.println("File ID: " + fileId);
                System.out.println("=======================");
                
            } catch (Exception e) {
                System.err.println("Error al subir archivo con File API, fallback a Base64: " + e.getMessage());
                usarFileAPI = false;
            }
        }
        
        if (!usarFileAPI) {
            // Método 2: Base64 en mensaje (para archivos pequeños o fallback)
            if (esPDF) {
                mensajeContenido = "Por favor, procesa este archivo PDF y extrae la información solicitada:\n\n" +
                        "INFORMACIÓN DEL ARCHIVO:\n" +
                        "- Nombre: " + file.getOriginalFilename() + "\n" +
                        "- Tipo: " + file.getContentType() + "\n" +
                        "- Tamaño: " + file.getSize() + " bytes\n\n" +
                        "El archivo está codificado en Base64 a continuación:\n\n" +
                        archivoBase64;
            } else {
                // Para imágenes, usar texto OCR como principal y archivo como respaldo
                mensajeContenido = "TEXTO EXTRAÍDO CON OCR:\n" + textoExtraido + "\n\n" +
                        "INFORMACIÓN DEL ARCHIVO:\n" +
                        "Nombre: " + file.getOriginalFilename() + "\n" +
                        "Tipo: " + file.getContentType() + "\n" +
                        "Tamaño: " + file.getSize() + " bytes";
                
                boolean incluirArchivo = (textoExtraido == null || textoExtraido.trim().isEmpty());
                if (incluirArchivo) {
                    mensajeContenido += "\n\n" + "ARCHIVO EN BASE64 (para respaldo):\n" + archivoBase64;
                }
            }
            
            System.out.println("=== USANDO BASE64 EN MENSAJE ===");
            System.out.println("Longitud del mensaje: " + mensajeContenido.length() + " caracteres");
            System.out.println("=================================");
        }

        try {
            // 1. Crear un thread
            Map<String, Object> threadRequest = new HashMap<>();
            HttpEntity<Map<String, Object>> threadEntity = new HttpEntity<>(threadRequest, headers);
            ResponseEntity<String> threadResponse = restTemplate.postForEntity(
                    "https://api.openai.com/v1/threads",
                    threadEntity,
                    String.class);
            String threadId = objectMapper.readTree(threadResponse.getBody()).get("id").asText();

            // 2. Añadir mensaje al thread
            Map<String, Object> messageRequest = new HashMap<>();
            messageRequest.put("role", "user");
            messageRequest.put("content", mensajeContenido);
            
            // Si usamos File API, agregar el attachment
            if (usarFileAPI && fileId != null) {
                List<Map<String, Object>> attachments = new ArrayList<>();
                Map<String, Object> attachment = new HashMap<>();
                attachment.put("file_id", fileId);
                attachment.put("tools", List.of(Map.of("type", "file_search")));
                attachments.add(attachment);
                messageRequest.put("attachments", attachments);
            }

            HttpEntity<Map<String, Object>> messageEntity = new HttpEntity<>(messageRequest, headers);
            restTemplate.postForEntity(
                    "https://api.openai.com/v1/threads/" + threadId + "/messages",
                    messageEntity,
                    String.class);

            // 3. Ejecutar el assistant
            Map<String, Object> runRequest = new HashMap<>();
            runRequest.put("assistant_id", documentRecognizerAssistantId);

            HttpEntity<Map<String, Object>> runEntity = new HttpEntity<>(runRequest, headers);
            ResponseEntity<String> runResponse = restTemplate.postForEntity(
                    "https://api.openai.com/v1/threads/" + threadId + "/runs",
                    runEntity,
                    String.class);

            String runId = objectMapper.readTree(runResponse.getBody()).get("id").asText();

            // 4. Esperar a que termine el run
            String status = "queued";
            while (!status.equals("completed")) {
                Thread.sleep(1000); // Esperar 1 segundo
                ResponseEntity<String> statusResponse = restTemplate.exchange(
                        "https://api.openai.com/v1/threads/" + threadId + "/runs/" + runId,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class);

                status = objectMapper.readTree(statusResponse.getBody()).get("status").asText();
                if (status.equals("failed") || status.equals("cancelled")) {
                    throw new RuntimeException("Document Recognizer run failed with status: " + status);
                }
            }

            // 5. Obtener mensajes
            ResponseEntity<String> messagesResponse = restTemplate.exchange(
                    "https://api.openai.com/v1/threads/" + threadId + "/messages",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);

            // Obtener el último mensaje del assistant
            JsonNode messages = objectMapper.readTree(messagesResponse.getBody()).get("data");
            String content = "";

            for (JsonNode message : messages) {
                if (message.get("role").asText().equals("assistant")) {
                    content = message.get("content").get(0).get("text").get("value").asText();
                    break;
                }
            }

            // Extraer el JSON del contenido
            String jsonContent = extractJsonFromContent(content);

            // Parsear el JSON limpio
            return objectMapper.readTree(jsonContent);

        } catch (Exception e) {
            System.err.println("Error al llamar a Document Recognizer Assistant: " + e.getMessage());
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
                String.class);
        String threadId = objectMapper.readTree(threadResponse.getBody()).get("id").asText();

        // 2. Añadir mensaje al thread
        Map<String, Object> messageRequest = new HashMap<>();
        messageRequest.put("role", "user");
        messageRequest.put("content", datosJson); // Solo enviamos el JSON, sin instrucciones

        HttpEntity<Map<String, Object>> messageEntity = new HttpEntity<>(messageRequest, headers);
        restTemplate.postForEntity(
                "https://api.openai.com/v1/threads/" + threadId + "/messages",
                messageEntity,
                String.class);

        // 3. Ejecutar el assistant
        Map<String, Object> runRequest = new HashMap<>();
        runRequest.put("assistant_id", documentOrganizerAssistantId);

        HttpEntity<Map<String, Object>> runEntity = new HttpEntity<>(runRequest, headers);
        ResponseEntity<String> runResponse = restTemplate.postForEntity(
                "https://api.openai.com/v1/threads/" + threadId + "/runs",
                runEntity,
                String.class);

        String runId = objectMapper.readTree(runResponse.getBody()).get("id").asText();

        // 4. Esperar a que termine el run
        String status = "queued";
        while (!status.equals("completed")) {
            Thread.sleep(1000); // Esperar 1 segundo
            ResponseEntity<String> statusResponse = restTemplate.exchange(
                    "https://api.openai.com/v1/threads/" + threadId + "/runs/" + runId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);

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
                String.class);

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

        // Para debugging - Mostrar la respuesta de la IA
        System.out.println("=== RESPUESTA DEL DOCUMENT ORGANIZER ===");
        System.out.println(content);
        System.out.println("==========================================");

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

    /**
     * Extrae el JSON válido del contenido de respuesta que puede contener texto
     * explicativo
     */
    private String extractJsonFromContent(String content) {
        // Si el contenido está en un bloque de código, extraerlo primero
        if (content.startsWith("```")) {
            int startIndex = content.indexOf('\n');
            if (startIndex != -1) {
                int endIndex = content.lastIndexOf("```");
                if (endIndex != -1) {
                    content = content.substring(startIndex + 1, endIndex).trim();
                }
            }
        }

        // Buscar el primer { que indica el inicio del JSON
        int jsonStart = content.indexOf('{');
        if (jsonStart == -1) {
            throw new RuntimeException("No se encontró JSON válido en la respuesta");
        }

        // Buscar el último } que cierra el JSON
        int jsonEnd = content.lastIndexOf('}');
        if (jsonEnd == -1 || jsonEnd <= jsonStart) {
            throw new RuntimeException("No se encontró JSON válido en la respuesta");
        }

        // Extraer solo la parte JSON
        String jsonContent = content.substring(jsonStart, jsonEnd + 1);

        // Validar que sea JSON válido
        try {
            objectMapper.readTree(jsonContent);
            return jsonContent;
        } catch (Exception e) {
            // Si no es JSON válido, intentar encontrar el JSON dentro del texto
            // Buscar patrones más específicos como "json" seguido de {
            int jsonKeywordIndex = content.toLowerCase().indexOf("json");
            if (jsonKeywordIndex != -1) {
                String afterJson = content.substring(jsonKeywordIndex + 4);
                int newJsonStart = afterJson.indexOf('{');
                if (newJsonStart != -1) {
                    int newJsonEnd = afterJson.lastIndexOf('}');
                    if (newJsonEnd != -1 && newJsonEnd > newJsonStart) {
                        return afterJson.substring(newJsonStart, newJsonEnd + 1);
                    }
                }
            }

            throw new RuntimeException("No se pudo extraer JSON válido: " + e.getMessage());
        }
    }

    // Método auxiliar para extraer valores de string de un JSON con valor por
    // defecto
    private String getStringValue(JsonNode node, String fieldName, String defaultValue) {
        if (node != null && node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asText();
        }
        return defaultValue;
    }

    /**
     * Procesa campos adicionales comunes del reconocimiento de documentos
     */
    private void procesarCamposAdicionales(JsonNode respuestaReconocimiento, ClasificacionCompletaDTO dto) {
        // Procesar tipoCategoria - convertir string a enum
        String tipoCategoriaStr = getStringValue(respuestaReconocimiento, "tipoCategoria", null);
        if (tipoCategoriaStr != null && !tipoCategoriaStr.equals("null")) {
            try {
                // Convertir el string a enum Categoria
                dto.setTipoCategoria(com.uade.tpo.keepit.enums.Categoria.valueOf(tipoCategoriaStr));
            } catch (IllegalArgumentException e) {
                System.err.println("Categoría no válida: " + tipoCategoriaStr);
                dto.setTipoCategoria(null);
            }
        }

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

        // Procesar la fecha de emisión si existe
        String fechaEmision = getStringValue(respuestaReconocimiento, "fechaEmision", null);
        if (fechaEmision != null && !fechaEmision.equals("null") && !fechaEmision.contains("_o_null")) {
            try {
                dto.setFechaEmision(java.sql.Date.valueOf(fechaEmision));
            } catch (Exception e) {
                System.err.println("Error al procesar la fecha de emisión: " + e.getMessage());
            }
        }

        // Procesar la fecha de vencimiento si existe
        String fechaVencimiento = getStringValue(respuestaReconocimiento, "fechaVencimiento", null);
        if (fechaVencimiento != null && !fechaVencimiento.equals("null")
                && !fechaVencimiento.contains("_o_null")) {
            try {
                dto.setFechaVencimiento(java.sql.Date.valueOf(fechaVencimiento));
            } catch (Exception e) {
                System.err.println("Error al procesar la fecha de vencimiento: " + e.getMessage());
            }
        }
    }
    
    /**
     * Sube un archivo a OpenAI usando la Files API
     */
    private String subirArchivoAOpenAI(MultipartFile file) throws Exception {
        try {
            // Crear headers para multipart/form-data
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            // Crear el cuerpo multipart
            org.springframework.util.LinkedMultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("file", new org.springframework.core.io.ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
            body.add("purpose", "assistants");

            HttpEntity<org.springframework.util.LinkedMultiValueMap<String, Object>> requestEntity = 
                    new HttpEntity<>(body, headers);

            // Subir archivo
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api.openai.com/v1/files", 
                    requestEntity, 
                    String.class);

            // Extraer file ID de la respuesta
            JsonNode responseNode = objectMapper.readTree(response.getBody());
            return responseNode.get("id").asText();
            
        } catch (Exception e) {
            System.err.println("Error al subir archivo a OpenAI: " + e.getMessage());
            throw new RuntimeException("No se pudo subir el archivo a OpenAI: " + e.getMessage(), e);
        }
    }
}