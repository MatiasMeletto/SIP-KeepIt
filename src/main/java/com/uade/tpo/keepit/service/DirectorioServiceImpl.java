package com.uade.tpo.keepit.service;

import com.uade.tpo.keepit.DTO.DirectorioDTO;
import com.uade.tpo.keepit.DTO.OrganizacionDirectoriosDTO;
import com.uade.tpo.keepit.entities.Archivo;
import com.uade.tpo.keepit.entities.Directorio;
import com.uade.tpo.keepit.entities.Usuario;
import com.uade.tpo.keepit.repository.ArchivoRepository;
import com.uade.tpo.keepit.repository.DirectorioRepository;
import com.uade.tpo.keepit.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DirectorioServiceImpl implements DirectorioService {
    
    @Autowired
    private DirectorioRepository directorioRepository;
    
    @Autowired
    private UserRepository usuarioRepository;
    
    @Autowired
    private ArchivoRepository archivoRepository;
    
    @Override
    public Directorio crearOObtenerDirectorio(String nombre, Directorio padre) {
        Optional<Directorio> existente;
        
        if (padre == null) {
            existente = directorioRepository.findByNombreAndPadreIsNullAndEliminadoFalse(nombre);
        } else {
            existente = directorioRepository.findByNombreAndPadreIdAndEliminadoFalse(nombre, padre.getId());
        }
        
        if (existente.isPresent()) {
            return existente.get();
        }
        
        Directorio nuevo = new Directorio();
        nuevo.setNombre(nombre);
        nuevo.setPadre(padre);
        nuevo.setEliminado(false);
        return directorioRepository.save(nuevo);
    }
    
    @Override
    public List<Directorio> obtenerDirectoriosRaiz() {
        return directorioRepository.findRootDirectoriesAndEliminadoFalse();
    }
    
    @Override
    public List<Directorio> obtenerTodos() {
        return directorioRepository.findAll();
    }
    
    @Override
    public Optional<DirectorioDTO> obtenerPorId(Long id) {
        return directorioRepository.findById(id)
                .filter(dir -> !dir.getEliminado())
                .map(this::toDto);
    }
    
    @Override
    public Directorio crearDirectorio(String nombre, Long padreId) {
        Directorio padre = null;
        if (padreId != null) {
            padre = directorioRepository.findById(padreId)
                    .orElseThrow(() -> new RuntimeException("Directorio padre no encontrado"));
        }
        return crearOObtenerDirectorio(nombre, padre);
    }
    
    // Implementación de los nuevos métodos
    @Override
    public Directorio guardarDirectorio(Directorio directorio) {
        return directorioRepository.save(directorio);
    }
    
    @Override
    public List<Directorio> obtenerTodosPorUsuario(Usuario usuario) {
        // Obtener directorios que tienen usuario nulo (compartidos) o el usuario especificado
        // y que no estén eliminados
        return directorioRepository.findAll().stream()
                .filter(dir -> !dir.getEliminado() && 
                       (dir.getUsuario() == null || 
                       (dir.getUsuario() != null && 
                        dir.getUsuario().getId().equals(usuario.getId()))))
                .collect(Collectors.toList());
    }
    
    @Override
    public Optional<Directorio> buscarPorId(Long id) {
        return directorioRepository.findById(id);
    }
    
    @Override
    public boolean eliminarDirectorio(Long id) {
        Optional<Directorio> directorio = directorioRepository.findById(id);
        if (directorio.isPresent() && !directorio.get().getEliminado()) {
            Directorio dir = directorio.get();
            
            // Verificar que no tenga archivos no eliminados
            Long cantidadArchivos = archivoRepository.countByDirectorioAndEliminadoFalse(dir);
            if (cantidadArchivos > 0) {
                // No eliminar si tiene archivos no eliminados
                return false;
            }
            
            // Verificar que no tenga subdirectorios no eliminados
            Long cantidadSubdirectorios = directorioRepository.countByPadreAndEliminadoFalse(dir);
            if (cantidadSubdirectorios > 0) {
                // No eliminar si tiene subdirectorios no eliminados
                return false;
            }
            
            // Marcar como eliminado (soft delete)
            dir.setEliminado(true);
            directorioRepository.save(dir);
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public Map<String, Object> obtenerEstructuraDirectorios(Usuario usuario) {
        List<Directorio> directoriosRaiz = directorioRepository.findByUsuarioIdAndPadreIsNullAndEliminadoFalse(usuario.getId());
        Map<String, Object> resultado = new HashMap<>();

        for (Directorio dir : directoriosRaiz) {
            resultado.put(dir.getNombre(), construirEstructuraDirectorio(dir));
        }

        return resultado;
    }

    @Transactional
    private Map<String, Object> construirEstructuraDirectorio(Directorio directorio) {
        Map<String, Object> nodoDirectorio = new HashMap<>();

        // Información del directorio
        nodoDirectorio.put("id", directorio.getId());
        nodoDirectorio.put("nombre", directorio.getNombre());

        // Subdirectorios
        Map<String, Object> subdirectorios = new HashMap<>();
        for (Directorio subdir : directorio.getSubdirectorios()) {
            if (!subdir.getEliminado()) {
                subdirectorios.put(subdir.getNombre(), construirEstructuraDirectorio(subdir));
            }
        }

        if (!subdirectorios.isEmpty()) {
            nodoDirectorio.put("subdirectorios", subdirectorios);
        }

        // Archivos (solo nombres, no contenido)
        if (directorio.getArchivos() != null && !directorio.getArchivos().isEmpty()) {
            List<Map<String, Object>> archivosInfo = new java.util.ArrayList<>();
            for (Archivo archivo : directorio.getArchivos()) {
                if (!archivo.getEliminado()) {
                    Map<String, Object> archivoInfo = new HashMap<>();
                    archivoInfo.put("id", archivo.getId());
                    archivoInfo.put("nombre", archivo.getNombre());
                    archivosInfo.add(archivoInfo);
                }
            }
            nodoDirectorio.put("archivos", archivosInfo);
        }

        return nodoDirectorio;
    }

    @Override
    @Transactional
    public List<DirectorioDTO> obtenerDirectoriosParaIA(Usuario usuario) {
        List<Directorio> directorios = obtenerTodosPorUsuario(usuario);
        List<DirectorioDTO> directoriosDTO = new ArrayList<>();
        
        for (Directorio dir : directorios) {
            DirectorioDTO dto = new DirectorioDTO();
            dto.setId_directorio(dir.getId().toString());
            dto.setNombre(dir.getNombre());
            
            if (dir.getPadre() != null) {
                dto.setId_directorio_padre(dir.getPadre().getId().toString());
            } else {
                dto.setId_directorio_padre(null);
            }
            
            // Obtener nombres de archivos
            List<String> nombresArchivos = new ArrayList<>();
            if (dir.getArchivos() != null) {
                for (Archivo archivo : dir.getArchivos()) {
                    if (!archivo.getEliminado()) {
                        nombresArchivos.add(archivo.getNombre());
                    }
                }
            }
            dto.setArchivos(nombresArchivos);
            
            directoriosDTO.add(dto);
        }
        
        return directoriosDTO;
    }
    
    @Override
    @Transactional
    public Map<String, Object> sincronizarDirectorios(OrganizacionDirectoriosDTO organizacionDTO, Usuario usuario) {
        // Resultado que devolveremos
        Map<String, Object> resultado = new HashMap<>();
        
        // 1. Obtener todos los directorios actuales del usuario
        List<Directorio> directoriosUsuario = obtenerTodosPorUsuario(usuario);
        
        // Mapa para facilitar la búsqueda por ID
        Map<String, Directorio> mapaDirectoriosActuales = new HashMap<>();
        for (Directorio dir : directoriosUsuario) {
            mapaDirectoriosActuales.put(dir.getId().toString(), dir);
        }
        
        // 2. Crear mapa que contendrá ID temporal -> ID real para directorios nuevos
        Map<String, String> mapeoIDsTemporales = new HashMap<>();
        
        // Primera pasada: Crear/actualizar directorios
        for (DirectorioDTO dirDTO : organizacionDTO.getDirectorios()) {
            String idDirectorio = dirDTO.getId_directorio();
            boolean esNuevo = idDirectorio.startsWith("-");
            
            if (esNuevo) {
                // Es un directorio nuevo
                Directorio nuevoDirect = new Directorio();
                nuevoDirect.setNombre(dirDTO.getNombre());
                nuevoDirect.setUsuario(usuario);
                nuevoDirect.setEliminado(false);
                
                // Guardar para obtener ID real
                Directorio guardado = directorioRepository.save(nuevoDirect);
                mapeoIDsTemporales.put(idDirectorio, guardado.getId().toString());
                
                System.out.println("Directorio creado: " + guardado.getId() + " - " + guardado.getNombre());
            } else {
                // Es un directorio existente, actualizar nombre si cambió
                Directorio existente = mapaDirectoriosActuales.get(idDirectorio);
                
                if (existente != null) {
                    if (!existente.getNombre().equals(dirDTO.getNombre())) {
                        existente.setNombre(dirDTO.getNombre());
                        existente = directorioRepository.save(existente);
                        System.out.println("Nombre actualizado para directorio " + existente.getId() + 
                                           ": " + existente.getNombre());
                    }
                } else {
                    System.err.println("No se encontró el directorio con ID: " + idDirectorio);
                }
            }
        }
        
        // Segunda pasada: Actualizar relaciones padre-hijo
        for (DirectorioDTO dirDTO : organizacionDTO.getDirectorios()) {
            String idDirectorio = dirDTO.getId_directorio();
            String idPadre = dirDTO.getId_directorio_padre();
            
            // Convertir ID temporal a real si es necesario
            String idDirectorioReal = mapeoIDsTemporales.getOrDefault(idDirectorio, idDirectorio);
            String idPadreReal = null;
            if (idPadre != null && !idPadre.equals("null")) {
                idPadreReal = mapeoIDsTemporales.getOrDefault(idPadre, idPadre);
            }
            
            // Obtener el directorio por su ID real
            Directorio directorio = directorioRepository.findById(Long.parseLong(idDirectorioReal)).orElse(null);
            
            if (directorio != null) {
                // Actualizar padre
                if (idPadreReal != null) {
                    Directorio padre = directorioRepository.findById(Long.parseLong(idPadreReal)).orElse(null);
                    
                    if (padre != null) {
                        boolean cambiarPadre = false;
                        
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
                    } else {
                        System.err.println("No se encontró el directorio padre con ID: " + idPadreReal);
                    }
                } else if (directorio.getPadre() != null) {
                    // Quitar padre (mover a raíz)
                    directorio.setPadre(null);
                    directorio = directorioRepository.save(directorio);
                    System.out.println("Directorio " + directorio.getId() + " movido a raíz");
                }
            } else {
                System.err.println("No se encontró el directorio con ID: " + idDirectorioReal);
            }
        }
        
        // Tercera pasada: Asignar archivos a directorios según la nueva estructura
        for (DirectorioDTO dirDTO : organizacionDTO.getDirectorios()) {
            String idDirectorio = dirDTO.getId_directorio();
            String idDirectorioReal = mapeoIDsTemporales.getOrDefault(idDirectorio, idDirectorio);
            
            Directorio directorio = directorioRepository.findById(Long.parseLong(idDirectorioReal)).orElse(null);
            
            if (directorio != null) {
                // Para cada archivo listado en este directorio, asignarlo aquí
                for (String nombreArchivo : dirDTO.getArchivos()) {
                    List<Archivo> archivosEncontrados = archivoRepository.findByNombreAndUsuarioAndEliminadoFalse(
                        nombreArchivo, usuario);
                    
                    if (!archivosEncontrados.isEmpty()) {
                        Archivo archivo = archivosEncontrados.get(0);
                        // Solo mover si no está ya en este directorio
                        if (archivo.getDirectorio() == null || !archivo.getDirectorio().getId().equals(directorio.getId())) {
                            archivo.setDirectorio(directorio);
                            archivoRepository.save(archivo);
                            System.out.println("Archivo " + archivo.getId() + " (" + archivo.getNombre() + 
                                            ") asignado al directorio " + directorio.getId() + " (" + directorio.getNombre() + ")");
                        }
                    } else {
                        System.err.println("No se encontró el archivo: " + nombreArchivo);
                    }
                }
            }
        }
        
        // 4. Preparar respuesta inicial (antes de la limpieza)
        List<Directorio> directoriosFinal = directorioRepository.findAllByUsuarioIdAndEliminadoFalse(usuario.getId());
        
        // 5. Marcar como eliminados todos los directorios vacíos (sin archivos no eliminados y sin subdirectorios no eliminados)
        // IMPORTANTE: Esto se ejecuta DESPUÉS de que todos los cambios se hayan persistido en la base de datos
        // Ejecutar múltiples veces para manejar directorios anidados vacíos
        boolean eliminados;
        do {
            eliminados = false;
            List<Directorio> todosDirectorios = directorioRepository.findByUsuarioAndEliminadoFalse(usuario);
            
            for (Directorio dir : todosDirectorios) {
                // Verificar que esté realmente vacío
                Long cantidadArchivos = archivoRepository.countByDirectorioAndEliminadoFalse(dir);
                
                // Contar subdirectorios no eliminados
                Long cantidadSubdirectorios = directorioRepository.countByPadreAndEliminadoFalse(dir);
                
                if (cantidadArchivos == 0 && cantidadSubdirectorios == 0) {
                    try {
                        dir.setEliminado(true);
                        directorioRepository.save(dir);
                        System.out.println("Directorio vacío marcado como eliminado: " + dir.getId() + " - " + dir.getNombre());
                        eliminados = true;
                    } catch (Exception e) {
                        System.err.println("Error al marcar directorio como eliminado " + dir.getId() + ": " + e.getMessage());
                    }
                }
            }
        } while (eliminados); // Repetir hasta que no se marque ningún directorio más como eliminado
        
        // 6. Actualizar la lista final después de la limpieza
        directoriosFinal = directorioRepository.findAllByUsuarioIdAndEliminadoFalse(usuario.getId());
        
        resultado.put("directorios", convertirDirectoriosMap(directoriosFinal));
        resultado.put("explicacion", organizacionDTO.getExplicacion());
        resultado.put("directoriosActualizados", directoriosFinal.stream()
                .map(this::mapDirectorioParaRespuesta)
                .collect(Collectors.toList()));
        
        return resultado;
    }
    
    @Override
    @Transactional
    public Map<String, Object> sincronizarDirectorios(OrganizacionDirectoriosDTO organizacionDTO, Usuario usuario, Archivo archivoCreado) {
        // Resultado que devolveremos
        Map<String, Object> resultado = new HashMap<>();
        
        // 1. Obtener todos los directorios actuales del usuario
        List<Directorio> directoriosUsuario = obtenerTodosPorUsuario(usuario);
        
        // Mapa para facilitar la búsqueda por ID
        Map<String, Directorio> mapaDirectoriosActuales = new HashMap<>();
        for (Directorio dir : directoriosUsuario) {
            mapaDirectoriosActuales.put(dir.getId().toString(), dir);
        }
        
        // 2. Crear mapa que contendrá ID temporal -> ID real para directorios nuevos
        Map<String, String> mapeoIDsTemporales = new HashMap<>();
        
        // Primera pasada: Crear/actualizar directorios
        for (DirectorioDTO dirDTO : organizacionDTO.getDirectorios()) {
            String idDirectorio = dirDTO.getId_directorio();
            boolean esNuevo = idDirectorio.startsWith("-");
            
            if (esNuevo) {
                // Es un directorio nuevo
                Directorio nuevoDirect = new Directorio();
                nuevoDirect.setNombre(dirDTO.getNombre());
                nuevoDirect.setUsuario(usuario);
                nuevoDirect.setEliminado(false);
                
                // Guardar para obtener ID real
                Directorio guardado = directorioRepository.save(nuevoDirect);
                mapeoIDsTemporales.put(idDirectorio, guardado.getId().toString());
                
                System.out.println("Directorio creado: " + guardado.getId() + " - " + guardado.getNombre());
            } else {
                // Es un directorio existente, actualizar nombre si cambió
                Directorio existente = mapaDirectoriosActuales.get(idDirectorio);
                
                if (existente != null) {
                    if (!existente.getNombre().equals(dirDTO.getNombre())) {
                        existente.setNombre(dirDTO.getNombre());
                        existente = directorioRepository.save(existente);
                        System.out.println("Nombre actualizado para directorio " + existente.getId() + 
                                           ": " + existente.getNombre());
                    }
                } else {
                    System.err.println("No se encontró el directorio con ID: " + idDirectorio);
                }
            }
        }
        
        // Segunda pasada: Actualizar relaciones padre-hijo
        for (DirectorioDTO dirDTO : organizacionDTO.getDirectorios()) {
            String idDirectorio = dirDTO.getId_directorio();
            String idPadre = dirDTO.getId_directorio_padre();
            
            // Convertir ID temporal a real si es necesario
            String idDirectorioReal = mapeoIDsTemporales.getOrDefault(idDirectorio, idDirectorio);
            String idPadreReal = null;
            if (idPadre != null && !idPadre.equals("null")) {
                idPadreReal = mapeoIDsTemporales.getOrDefault(idPadre, idPadre);
            }
            
            // Obtener el directorio por su ID real
            Directorio directorio = directorioRepository.findById(Long.parseLong(idDirectorioReal)).orElse(null);
            
            if (directorio != null) {
                // Actualizar padre
                if (idPadreReal != null) {
                    Directorio padre = directorioRepository.findById(Long.parseLong(idPadreReal)).orElse(null);
                    
                    if (padre != null) {
                        boolean cambiarPadre = false;
                        
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
                    } else {
                        System.err.println("No se encontró el directorio padre con ID: " + idPadreReal);
                    }
                } else if (directorio.getPadre() != null) {
                    // Quitar padre (mover a raíz)
                    directorio.setPadre(null);
                    directorio = directorioRepository.save(directorio);
                    System.out.println("Directorio " + directorio.getId() + " movido a raíz");
                }
            } else {
                System.err.println("No se encontró el directorio con ID: " + idDirectorioReal);
            }
        }
        
        // Tercera pasada: Asignar el archivo creado al directorio correcto
        // MODIFICADO: Ahora usamos el archivo que ya fue creado y persistido
        for (DirectorioDTO dirDTO : organizacionDTO.getDirectorios()) {
            if (!dirDTO.getArchivos().isEmpty()) {
                String idDirectorio = dirDTO.getId_directorio();
                String idDirectorioReal = mapeoIDsTemporales.getOrDefault(idDirectorio, idDirectorio);
                
                Directorio directorio = directorioRepository.findById(Long.parseLong(idDirectorioReal)).orElse(null);
                
                if (directorio != null) {
                    // Verificar si el archivo creado debe ir en este directorio
                    for (String nombreArchivo : dirDTO.getArchivos()) {
                        if (archivoCreado.getNombre().equals(nombreArchivo)) {
                            // Asignar el archivo al directorio
                            archivoCreado.setDirectorio(directorio);
                            archivoRepository.save(archivoCreado);
                            System.out.println("Archivo " + archivoCreado.getId() + " (" + archivoCreado.getNombre() + 
                                            ") asignado al directorio " + directorio.getId() + " (" + directorio.getNombre() + ")");
                            break; // Solo necesitamos asignar una vez
                        }
                    }
                }
            }
        }
        
        // 4. Preparar respuesta inicial (antes de la limpieza)
        List<Directorio> directoriosFinal = directorioRepository.findAllByUsuarioIdAndEliminadoFalse(usuario.getId());
        
        // 5. Marcar como eliminados todos los directorios vacíos (sin archivos no eliminados y sin subdirectorios no eliminados)
        // IMPORTANTE: Esto se ejecuta DESPUÉS de que todos los cambios se hayan persistido en la base de datos
        // Ejecutar múltiples veces para manejar directorios anidados vacíos
        boolean eliminados;
        do {
            eliminados = false;
            List<Directorio> todosDirectorios = directorioRepository.findByUsuarioAndEliminadoFalse(usuario);
            
            for (Directorio dir : todosDirectorios) {
                // Verificar que esté realmente vacío
                Long cantidadArchivos = archivoRepository.countByDirectorioAndEliminadoFalse(dir);
                
                // Contar subdirectorios no eliminados
                Long cantidadSubdirectorios = directorioRepository.countByPadreAndEliminadoFalse(dir);
                
                if (cantidadArchivos == 0 && cantidadSubdirectorios == 0) {
                    try {
                        dir.setEliminado(true);
                        directorioRepository.save(dir);
                        System.out.println("Directorio vacío marcado como eliminado: " + dir.getId() + " - " + dir.getNombre());
                        eliminados = true;
                    } catch (Exception e) {
                        System.err.println("Error al marcar directorio como eliminado " + dir.getId() + ": " + e.getMessage());
                    }
                }
            }
        } while (eliminados); // Repetir hasta que no se marque ningún directorio más como eliminado
        
        // 6. Actualizar la lista final después de la limpieza
        directoriosFinal = directorioRepository.findAllByUsuarioIdAndEliminadoFalse(usuario.getId());
        
        resultado.put("directorios", convertirDirectoriosMap(directoriosFinal));
        resultado.put("explicacion", organizacionDTO.getExplicacion());
        resultado.put("directoriosActualizados", directoriosFinal.stream()
                .map(this::mapDirectorioParaRespuesta)
                .collect(Collectors.toList()));
        
        return resultado;
    }
    
    private Map<String, Object> convertirDirectoriosMap(List<Directorio> directorios) {
        Map<String, Object> directoriosMap = new HashMap<>();
        
        for (Directorio dir : directorios) {
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
            directoriosMap.put(dir.getId().toString(), dirInfo);
        }
        
        return directoriosMap;
    }
    
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

    @Override
    public Directorio obtenerPorNombre(String nombre, Usuario usuario) {
        // First try to find directory by name and user
        List<Directorio> directorios = directorioRepository.findAllByUsuarioIdAndEliminadoFalse(usuario.getId());
        Optional<Directorio> directorio = directorios.stream()
            .filter(d -> d.getNombre().equals(nombre) && d.getPadre() == null && !d.getEliminado())
            .findFirst();
        
        if (directorio.isPresent()) {
            return directorio.get();
        } else {
            // Check if another user's directory with the same name exists that can be shared
            Optional<Directorio> sharedDir = directorioRepository.findByNombreAndPadreIsNullAndUsuarioIsNull(nombre);
            if (sharedDir.isPresent()) {
                return sharedDir.get();
            }
            
            // If no directory exists with this name for this user, create it
            Directorio nuevo = new Directorio();
            nuevo.setNombre(nombre);
            nuevo.setUsuario(usuario);
            nuevo.setEliminado(false);
            return directorioRepository.save(nuevo);
        }
    }

    @Override
    @Transactional
    public List<Map<String, Object>> obtenerDirectoriosConArchivos(Usuario usuario) {
        List<Directorio> todosDirectorios = obtenerTodosPorUsuario(usuario);
        List<Map<String, Object>> resultado = new ArrayList<>();
        
        for (Directorio dir : todosDirectorios) {
            // Verificar si el directorio tiene archivos no eliminados
            boolean tieneArchivos = dir.getArchivos() != null && 
                               dir.getArchivos().stream()
                                  .anyMatch(a -> !a.getEliminado());
        
            if (tieneArchivos) {
                Map<String, Object> dirMap = new HashMap<>();
                dirMap.put("id", dir.getId());
                dirMap.put("nombre", dir.getNombre());
            
                if (dir.getPadre() != null) {
                    dirMap.put("padreId", dir.getPadre().getId());
                    dirMap.put("padreNombre", dir.getPadre().getNombre());
                } else {
                    dirMap.put("padreId", null);
                    dirMap.put("padreNombre", null);
                }
            
                // Añadir lista de archivos
                List<Map<String, Object>> archivos = dir.getArchivos().stream()
                .filter(a -> !a.getEliminado())
                .map(a -> {
                    Map<String, Object> archivoMap = new HashMap<>();
                    archivoMap.put("id", a.getId());
                    archivoMap.put("nombre", a.getNombre());
                    return archivoMap;
                })
                .collect(Collectors.toList());
            
                dirMap.put("archivos", archivos);
                resultado.add(dirMap);
            }
        }
    
        return resultado;
    }
    
    @Override
    public List<DirectorioDTO> obtenerByEmail(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con email: " + email));
        List<Directorio> directorios = directorioRepository.findByUsuarioAndEliminadoFalse(usuario);
        return directorios.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public DirectorioDTO toDto(Directorio directorio) {
        DirectorioDTO dto = new DirectorioDTO();
        dto.setId_directorio(directorio.getId().toString());
        dto.setId_directorio_padre(directorio.getPadre() != null ? directorio.getPadre().getId().toString() : null);
        dto.setNombre(directorio.getNombre());
        // Mapear otros campos según sea necesario
        return dto;
    }
}