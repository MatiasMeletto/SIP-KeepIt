package com.uade.tpo.keepit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import com.uade.tpo.keepit.DTO.ClasificacionCompletaDTO;
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
import com.google.cloud.vision.v1.ImageAnnotatorSettings;
import java.io.FileInputStream;
import java.io.InputStream;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

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
            // 1. Obtener estructura de directorios del usuario
            Map<String, Object> estructuraDirectorios = obtenerEstructuraDirectorios(usuario);
            
            // 2. Llamar al Assistant de Document Organizer
            JsonNode respuestaOrganizacion = llamarDocumentOrganizer(clasificacion, estructuraDirectorios);
            
            // 3. Sincronizar estructura de directorios según la respuesta
            Map<String, Object> resultadoFinal = sincronizarDirectorios(respuestaOrganizacion, usuario);
            
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
        
        // Construir el cuerpo del mensaje - IMPORTANTE: incluir la palabra "json" en el mensaje
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
        // Especificar que queremos una respuesta en formato JSON
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
    private JsonNode llamarDocumentOrganizer(ClasificacionCompletaDTO clasificacion, Map<String, Object> directorios) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        
        // Construir el mensaje con información del documento y estructura de directorios
        // IMPORTANTE: Incluir la palabra "json" en el mensaje
        StringBuilder mensajeBuilder = new StringBuilder();
        mensajeBuilder.append("Necesito organizar este documento. Responde con un objeto JSON con los campos 'directorios' y 'explicacion':\n");
        mensajeBuilder.append("Nombre: ").append(clasificacion.getNombre()).append("\n");
        mensajeBuilder.append("Tipo: ").append(clasificacion.getTipoArchivoDetectado()).append("\n");
        mensajeBuilder.append("Resumen: ").append(clasificacion.getResumen()).append("\n");
        
        if (clasificacion.getEmisor() != null) {
            mensajeBuilder.append("Emisor: ").append(clasificacion.getEmisor()).append("\n");
        }
        
        if (clasificacion.getNombreTitular() != null) {
            mensajeBuilder.append("Titular: ").append(clasificacion.getNombreTitular()).append("\n");
        }
        
        if (clasificacion.getMonto() != null) {
            mensajeBuilder.append("Monto: ").append(clasificacion.getMonto()).append("\n");
        }
        
        mensajeBuilder.append("\nEstructura de directorios actual:\n");
        mensajeBuilder.append(objectMapper.writeValueAsString(directorios));
        mensajeBuilder.append("\n\nPor favor, proporciona tu recomendación en formato JSON con la estructura de directorios.");
        
        // Preparar cuerpo de la solicitud
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", mensajeBuilder.toString());
    
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o");
        requestBody.put("messages", Collections.singletonList(message));
        requestBody.put("temperature", 0.1);
        requestBody.put("response_format", Map.of("type", "json_object"));
    
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
    
        try {
            System.out.println("Enviando solicitud a OpenAI API para organización...");
            ResponseEntity<String> response = restTemplate.postForEntity(
                apiUrl,
                request, 
                String.class
            );
            
            System.out.println("Respuesta de OpenAI Organizer: " + response.getBody());
            
            if (response.getStatusCode() == HttpStatus.OK) {
                // Extraer el contenido del mensaje de respuesta
                JsonNode responseNode = objectMapper.readTree(response.getBody());
                String responseContent = responseNode.path("choices").path(0).path("message").path("content").asText();
                
                // Intentar parsear la respuesta como JSON
                try {
                    return objectMapper.readTree(responseContent);
                } catch (Exception e) {
                    System.err.println("Error al parsear respuesta como JSON: " + e.getMessage());
                    
                    // Crear una estructura básica por defecto
                    ObjectNode defaultNode = objectMapper.createObjectNode();
                    ObjectNode directorios_node = objectMapper.createObjectNode();
                    defaultNode.set("directorios", directorios_node);
                    defaultNode.put("explicacion", "No se pudo procesar la organización. Se sugiere ubicar el archivo en su directorio actual.");
                    
                    return defaultNode;
                }
            } else {
                throw new RuntimeException("Error en la respuesta de OpenAI: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("Error al llamar a OpenAI API Organizer: " + e.getMessage());
            e.printStackTrace();
            
            // Devolver un objeto JSON por defecto en caso de error
            ObjectNode defaultNode = objectMapper.createObjectNode();
            ObjectNode directorios_node = objectMapper.createObjectNode();
            defaultNode.set("directorios", directorios_node);
            defaultNode.put("explicacion", "No se pudo procesar la organización debido a un error: " + e.getMessage());
            
            return defaultNode;
        }
    }
    
    /**
     * Crea un DTO desde la respuesta del reconocimiento
     */
    private ClasificacionCompletaDTO crearDTODesdeReconocimiento(JsonNode jsonNode, MultipartFile file) throws Exception {
        ClasificacionCompletaDTO dto = new ClasificacionCompletaDTO();
        
        // Campos básicos del archivo
        dto.setNombre(file.getOriginalFilename());
        dto.setTipoExtension(file.getContentType());
        dto.setTamanioBytes(file.getSize());
        dto.setContenidoArchivo(Base64.getEncoder().encodeToString(file.getBytes()));
        
        // Campos extraídos por IA
        dto.setTipoArchivoDetectado(getStringValue(jsonNode, "tipoArchivoDetectado"));
        dto.setResumen(getStringValue(jsonNode, "resumen"));
        dto.setNombreTitular(getStringValue(jsonNode, "nombreTitular"));
        dto.setEmisor(getStringValue(jsonNode, "emisor"));
        dto.setNumeroFactura(getStringValue(jsonNode, "numeroFactura"));
        
        // Monto
        if (jsonNode.has("monto") && !jsonNode.get("monto").isNull()) {
            try {
                if (jsonNode.get("monto").isNumber()) {
                    dto.setMonto(jsonNode.get("monto").asDouble());
                } else {
                    String montoStr = jsonNode.get("monto").asText();
                    if (!"null".equalsIgnoreCase(montoStr)) {
                        dto.setMonto(Double.parseDouble(montoStr.replaceAll("[^\\d.]", "")));
                    }
                }
            } catch (Exception e) {
                System.err.println("Error al parsear monto: " + e.getMessage());
            }
        }
        
        // Fecha
        String fechaEmision = getStringValue(jsonNode, "fechaEmision");
        if (fechaEmision != null) {
            try {
                dto.setFechaEmision(java.sql.Date.valueOf(fechaEmision.replace("_o_null", "")));
            } catch (Exception e) {
                System.err.println("Error al parsear fecha: " + e.getMessage());
            }
        }
        
        return dto;
    }
    
    /**
     * Obtiene la estructura de directorios para un usuario
     */
    private Map<String, Object> obtenerEstructuraDirectorios(Usuario usuario) {
        // Obtener todos los directorios del usuario
        List<Directorio> directorios = directorioRepository.findAllByUsuarioId(usuario.getId());
        
        // Crear mapa de estructura de directorios según formato esperado por el Assistant
        Map<String, Object> directorioMap = new HashMap<>();
        
        // Convertir cada directorio al formato requerido
        for (Directorio dir : directorios) {
            Map<String, Object> dirInfo = new HashMap<>();
            
            // ID del directorio padre (null si es raíz)
            if (dir.getPadre() != null) {
                dirInfo.put("id_directorio_padre", dir.getPadre().getId().toString());
            } else {
                dirInfo.put("id_directorio_padre", "null");
            }
            
            // Lista de archivos
            List<String> archivos = dir.getArchivos() != null ?
                dir.getArchivos().stream()
                    .filter(archivo -> !archivo.getEliminado())
                    .map(archivo -> archivo.getNombre())
                    .collect(Collectors.toList()) :
                new ArrayList<>();
                
            dirInfo.put("archivos", archivos);
            
            // Nombre del directorio (información adicional útil)
            dirInfo.put("nombre", dir.getNombre());
            
            // Agregar al mapa principal usando el ID como clave
            directorioMap.put(dir.getId().toString(), dirInfo);
        }
        
        return directorioMap;
    }
    
    /**
     * Sincroniza la estructura de directorios según la respuesta del assistant
     */
    private Map<String, Object> sincronizarDirectorios(JsonNode respuestaJson, Usuario usuario) {
        Map<String, Object> resultado = new HashMap<>();
        
        // Verificar que existe el nodo "directorios" en la respuesta
        if (!respuestaJson.has("directorios")) {
            throw new RuntimeException("La respuesta del assistant no contiene el campo 'directorios'");
        }
        
        // Obtener el nodo de directorios y la explicación
        JsonNode directoriosNode = respuestaJson.get("directorios");
        String explicacion = getStringValue(respuestaJson, "explicacion");
        
        // Convertir la respuesta a un mapa
        Map<String, Map<String, Object>> directoriosSugeridos = new HashMap<>();
        
        // Iterar todos los directorios en la respuesta
        directoriosNode.fields().forEachRemaining(entry -> {
            String dirId = entry.getKey();
            JsonNode dirInfo = entry.getValue();
            
            Map<String, Object> infoMap = new HashMap<>();
            
            // ID del padre
            if (dirInfo.has("id_directorio_padre")) {
                String padreId = dirInfo.get("id_directorio_padre").asText();
                infoMap.put("id_directorio_padre", "null".equals(padreId) ? null : padreId);
            } else {
                infoMap.put("id_directorio_padre", null);
            }
            
            // Nombre del directorio (si está presente)
            if (dirInfo.has("nombre")) {
                infoMap.put("nombre", dirInfo.get("nombre").asText());
            }
            
            // Archivos
            List<String> archivos = new ArrayList<>();
            if (dirInfo.has("archivos") && dirInfo.get("archivos").isArray()) {
                dirInfo.get("archivos").forEach(archivo -> archivos.add(archivo.asText()));
            }
            infoMap.put("archivos", archivos);
            
            // Agregar al mapa de directorios sugeridos
            directoriosSugeridos.put(dirId, infoMap);
        });
        
        // 1. Obtener la estructura actual de directorios del usuario
        List<Directorio> directoriosActuales = directorioRepository.findAllByUsuarioId(usuario.getId());
        Map<String, Directorio> mapDirectoriosActuales = directoriosActuales.stream()
                .collect(Collectors.toMap(d -> d.getId().toString(), d -> d));
        
        // 2. Comparar y actualizar directorios
        
        // 2.1 Eliminar directorios que no estén en la estructura sugerida (solo los vacíos)
        List<String> idsActuales = new ArrayList<>(mapDirectoriosActuales.keySet());
        List<String> idsSugeridos = new ArrayList<>(directoriosSugeridos.keySet());
        
        // Directorios a eliminar: los que están en actuales pero no en sugeridos
        for (String idActual : idsActuales) {
            if (!idsSugeridos.contains(idActual)) {
                Directorio directorio = mapDirectoriosActuales.get(idActual);
                
                // Solo eliminar si no tiene archivos
                if (directorio.getArchivos() == null || directorio.getArchivos().isEmpty()) {
                    try {
                        directorioRepository.delete(directorio);
                        System.out.println("Directorio eliminado: " + directorio.getId() + " - " + directorio.getNombre());
                    } catch (Exception e) {
                        // Si da error, probablemente tiene referencias, ignoramos
                        System.err.println("Error al eliminar directorio " + directorio.getId() + ": " + e.getMessage());
                    }
                }
            }
        }
        
        // 2.2 Crear o actualizar directorios según la estructura sugerida
        Map<String, Directorio> directoriosActualizados = new HashMap<>();
        
        // Primera pasada: crear directorios que faltan
        for (String idSugerido : idsSugeridos) {
            Map<String, Object> infoSugerida = directoriosSugeridos.get(idSugerido);
            
            if (!mapDirectoriosActuales.containsKey(idSugerido)) {
                // Crear directorio nuevo
                Directorio nuevoDirect = new Directorio();
                
                // Si el ID es numérico, intentar usarlo
                try {
                    Long idNuevo = Long.parseLong(idSugerido);
                    nuevoDirect.setId(idNuevo);
                } catch (NumberFormatException e) {
                    // El ID no es numérico, se asignará uno automáticamente
                }
                
                // Asignar nombre del directorio si está presente
                if (infoSugerida.containsKey("nombre")) {
                    nuevoDirect.setNombre((String) infoSugerida.get("nombre"));
                } else {
                    nuevoDirect.setNombre("Nuevo_" + idSugerido);
                }
                
                nuevoDirect.setUsuario(usuario);
                
                // El padre se asignará en la segunda pasada
                Directorio guardado = directorioRepository.save(nuevoDirect);
                directoriosActualizados.put(idSugerido, guardado);
                System.out.println("Directorio creado: " + guardado.getId() + " - " + guardado.getNombre());
            } else {
                // Mantener el directorio existente para actualización
                Directorio existente = mapDirectoriosActuales.get(idSugerido);
                
                // Actualizar nombre si cambió
                if (infoSugerida.containsKey("nombre") && 
                    !existente.getNombre().equals(infoSugerida.get("nombre"))) {
                    
                    existente.setNombre((String) infoSugerida.get("nombre"));
                    existente = directorioRepository.save(existente);
                    System.out.println("Nombre actualizado para directorio " + existente.getId() + 
                                       ": " + existente.getNombre());
                }
                
                directoriosActualizados.put(idSugerido, existente);
            }
        }
        
        // Segunda pasada: actualizar relaciones padre-hijo
        for (String idSugerido : idsSugeridos) {
            Directorio directorio = directoriosActualizados.get(idSugerido);
            Map<String, Object> infoSugerida = directoriosSugeridos.get(idSugerido);
            
            if (directorio != null) {
                // Obtener el ID del padre
                String idPadre = (String) infoSugerida.get("id_directorio_padre");
                
                // Actualizar padre si es necesario
                if (idPadre != null && !"null".equals(idPadre)) {
                    Directorio padre = directoriosActualizados.getOrDefault(
                            idPadre, mapDirectoriosActuales.get(idPadre));
                    
                    // Verificar si el padre ha cambiado
                    boolean cambiarPadre = false;
                    
                    if (padre != null) {
                        if (directorio.getPadre() == null) {
                            cambiarPadre = true;
                        } else if (!padre.getId().equals(directorio.getPadre().getId())) {
                            cambiarPadre = true;
                        }
                        
                        if (cambiarPadre) {
                            directorio.setPadre(padre);
                            directorio = directorioRepository.save(directorio);
                            System.out.println("Actualizado padre de directorio " + directorio.getId() + 
                                    " a " + padre.getId() + " (" + padre.getNombre() + ")");
                        }
                    }
                } else if (directorio.getPadre() != null) {
                    // Establecer a nulo si antes tenía un padre y ahora no debe tenerlo
                    directorio.setPadre(null);
                    directorio = directorioRepository.save(directorio);
                    System.out.println("Directorio " + directorio.getId() + " movido a raíz");
                }
            }
        }
        
        // 3. Preparar respuesta
        // Convertimos la estructura actualizada a formato de respuesta
        Map<String, Object> directoriosRespuesta = new HashMap<>();
        
        // Obtener los directorios actualizados después de todas las operaciones
        List<Directorio> directoriosFinal = directorioRepository.findAllByUsuarioId(usuario.getId());
        
        for (Directorio dir : directoriosFinal) {
            Map<String, Object> dirInfo = new HashMap<>();
            
            // Información del padre
            if (dir.getPadre() != null) {
                dirInfo.put("id_directorio_padre", dir.getPadre().getId().toString());
            } else {
                dirInfo.put("id_directorio_padre", "null");
            }
            
            // Nombre
            dirInfo.put("nombre", dir.getNombre());
            
            // Archivos
            List<String> archivos = dir.getArchivos() != null ?
                dir.getArchivos().stream()
                    .filter(archivo -> !archivo.getEliminado())
                    .map(archivo -> archivo.getNombre())
                    .collect(Collectors.toList()) :
                new ArrayList<>();
                
            dirInfo.put("archivos", archivos);
            
            // Agregar al mapa de directorios
            directoriosRespuesta.put(dir.getId().toString(), dirInfo);
        }
        
        resultado.put("directorios", directoriosRespuesta);
        resultado.put("explicacion", explicacion);
        resultado.put("directoriosActualizados", directoriosFinal.stream()
                .map(this::mapDirectorioParaRespuesta)
                .collect(Collectors.toList()));
        
        return resultado;
    }
    
    /**
     * Mapea un directorio a un formato simplificado para la respuesta
     */
    private Map<String, Object> mapDirectorioParaRespuesta(Directorio directorio) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", directorio.getId());
        map.put("nombre", directorio.getNombre());
        
        if (directorio.getPadre() != null) {
            map.put("padreId", directorio.getPadre().getId());
            map.put("padreNombre", directorio.getPadre().getNombre());
        } else {
            map.put("padreId", null);
            map.put("padreNombre", null);
        }
        
        return map;
    }
    
    /**
     * Método helper para extraer string de JSON
     */
    private String getStringValue(JsonNode node, String fieldName) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asText();
        }
        return null;
    }

    // Método auxiliar para extraer valores de string de un JSON con valor por defecto
    private String getStringValue(JsonNode node, String fieldName, String defaultValue) {
        if (node != null && node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asText();
        }
        return defaultValue;
    }
}