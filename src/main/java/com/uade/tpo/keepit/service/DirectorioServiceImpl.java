package com.uade.tpo.keepit.service;

import com.uade.tpo.keepit.DTO.DirectorioDTO;
import com.uade.tpo.keepit.DTO.OrganizacionDirectoriosDTO;
import com.uade.tpo.keepit.entities.Archivo;
import com.uade.tpo.keepit.entities.Directorio;
import com.uade.tpo.keepit.entities.Usuario;
import com.uade.tpo.keepit.repository.ArchivoRepository;
import com.uade.tpo.keepit.repository.DirectorioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
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
    private ArchivoService archivoService;
    
    @Autowired
    private ArchivoRepository archivoRepository;
    
    @Override
    public Directorio crearOObtenerDirectorio(String nombre, Directorio padre) {
        Optional<Directorio> existente;
        
        if (padre == null) {
            existente = directorioRepository.findByNombreAndPadreIsNull(nombre);
        } else {
            existente = directorioRepository.findByNombreAndPadreId(nombre, padre.getId());
        }
        
        if (existente.isPresent()) {
            return existente.get();
        }
        
        Directorio nuevo = new Directorio();
        nuevo.setNombre(nombre);
        nuevo.setPadre(padre);
        return directorioRepository.save(nuevo);
    }
    
    @Override
    public List<Directorio> obtenerDirectoriosRaiz() {
        return directorioRepository.findRootDirectories();
    }
    
    @Override
    public List<Directorio> obtenerTodos() {
        return directorioRepository.findAll();
    }
    
    @Override
    public Optional<Directorio> obtenerPorId(Long id) {
        return directorioRepository.findById(id);
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
        return directorioRepository.findAll().stream()
                .filter(dir -> dir.getUsuario() == null || 
                       (dir.getUsuario() != null && 
                        dir.getUsuario().getId().equals(usuario.getId())))
                .collect(Collectors.toList());
    }
    
    @Override
    public Optional<Directorio> buscarPorId(Long id) {
        return directorioRepository.findById(id);
    }
    
    @Override
    public boolean eliminarDirectorio(Long id) {
        if (directorioRepository.existsById(id)) {
            // Verificar que no tenga archivos
            int cantidadArchivos = directorioRepository.countArchivosByDirectorioId(id);
            if (cantidadArchivos > 0) {
                // No eliminar si tiene archivos
                return false;
            }
            
            // Eliminar solo si no tiene archivos
            directorioRepository.deleteById(id);
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public Map<String, Object> obtenerEstructuraDirectorios(Usuario usuario) {
        List<Directorio> directoriosRaiz = directorioRepository.findByUsuarioIdAndPadreIsNull(usuario.getId());
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
            subdirectorios.put(subdir.getNombre(), construirEstructuraDirectorio(subdir));
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
        
        // 2. Creamos un mapa para los directorios actualizados/nuevos
        Map<String, Directorio> directoriosActualizados = new HashMap<>();
        
        // Primera pasada: Actualizar/crear directorios
        for (DirectorioDTO dirDTO : organizacionDTO.getDirectorios()) {
            String idDirectorio = dirDTO.getId_directorio();
            boolean esNuevo = idDirectorio.startsWith("-");
            
            if (esNuevo) {
                // Es un directorio nuevo
                Directorio nuevoDirect = new Directorio();
                nuevoDirect.setNombre(dirDTO.getNombre());
                nuevoDirect.setUsuario(usuario);
                nuevoDirect.setSubdirectorios(new ArrayList<>());
                nuevoDirect.setArchivos(new ArrayList<>());
                
                // Guardar para obtener ID
                Directorio guardado = directorioRepository.save(nuevoDirect);
                directoriosActualizados.put(idDirectorio, guardado);
                
                System.out.println("Directorio creado: " + guardado.getId() + " - " + guardado.getNombre());
            } else {
                // Es un directorio existente
                Directorio existente = mapaDirectoriosActuales.get(idDirectorio);
                
                if (existente != null) {
                    // Actualizar nombre si cambió
                    if (!existente.getNombre().equals(dirDTO.getNombre())) {
                        existente.setNombre(dirDTO.getNombre());
                        existente = directorioRepository.save(existente);
                        System.out.println("Nombre actualizado para directorio " + existente.getId() + 
                                           ": " + existente.getNombre());
                    }
                    
                    directoriosActualizados.put(idDirectorio, existente);
                    // Eliminar del mapa original para saber cuáles borrar al final
                    mapaDirectoriosActuales.remove(idDirectorio);
                } else {
                    System.err.println("No se encontró el directorio con ID: " + idDirectorio);
                }
            }
        }
        
        // Segunda pasada: Actualizar relaciones padre-hijo y archivos
        for (DirectorioDTO dirDTO : organizacionDTO.getDirectorios()) {
            String idDirectorio = dirDTO.getId_directorio();
            String idPadre = dirDTO.getId_directorio_padre();
            
            // Obtener el directorio (nuevo o existente)
            Directorio directorio = directoriosActualizados.get(idDirectorio);
            
            if (directorio != null) {
                // Actualizar padre
                if (idPadre != null && !idPadre.equals("null")) {
                    Directorio padre = directoriosActualizados.get(idPadre);
                    if (padre == null) {
                        // Podría ser un directorio existente que no se tocó
                        padre = mapaDirectoriosActuales.get(idPadre);
                    }
                    
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
                    }
                } else if (directorio.getPadre() != null) {
                    // Quitar padre (mover a raíz)
                    directorio.setPadre(null);
                    directorio = directorioRepository.save(directorio);
                }
                
                // Manejar archivos
                actualizarArchivosDirectorio(directorio, dirDTO.getArchivos(), usuario);
            }
        }
        
        // 3. Eliminar directorios vacíos que ya no están en la estructura
        for (Directorio directorioSobrante : mapaDirectoriosActuales.values()) {
            // Solo eliminar si no tiene archivos y no tiene subdirectorios
            if ((directorioSobrante.getArchivos() == null || directorioSobrante.getArchivos().isEmpty()) &&
                (directorioSobrante.getSubdirectorios() == null || directorioSobrante.getSubdirectorios().isEmpty())) {
                try {
                    directorioRepository.delete(directorioSobrante);
                    System.out.println("Directorio eliminado: " + directorioSobrante.getId() + " - " + directorioSobrante.getNombre());
                } catch (Exception e) {
                    System.err.println("Error al eliminar directorio " + directorioSobrante.getId() + ": " + e.getMessage());
                }
            }
        }
        
        // 4. Preparar respuesta
        List<Directorio> directoriosFinal = directorioRepository.findAllByUsuarioId(usuario.getId());
        
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
        List<Directorio> directorios = directorioRepository.findAllByUsuarioId(usuario.getId());
        Optional<Directorio> directorio = directorios.stream()
            .filter(d -> d.getNombre().equals(nombre) && d.getPadre() == null)
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
            nuevo.setSubdirectorios(new ArrayList<>());
            nuevo.setArchivos(new ArrayList<>());
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
    
    @Transactional
    private void actualizarArchivosDirectorio(Directorio directorio, List<String> archivosDeseados, Usuario usuario) {
        // Obtener los archivos actuales del directorio
        List<Archivo> archivosActuales = directorio.getArchivos() != null ? 
            directorio.getArchivos().stream()
                .filter(a -> !a.getEliminado())
                .collect(Collectors.toList()) : 
            new ArrayList<>();
        
        // Mapa para búsqueda rápida de archivos por nombre
        Map<String, Archivo> mapaArchivos = new HashMap<>();
        for (Archivo a : archivosActuales) {
            mapaArchivos.put(a.getNombre(), a);
        }
        
        // Para cada archivo deseado, ver si ya existe o hay que moverlo
        for (String nombreArchivo : archivosDeseados) {
            if (mapaArchivos.containsKey(nombreArchivo)) {
                // Ya está en este directorio, no hacer nada
                mapaArchivos.remove(nombreArchivo); // Quitar para saber cuáles sobran
            } else {
                // Buscar el archivo por nombre y usuario
                List<Archivo> archivosEncontrados = archivoRepository.findByNombreAndUsuarioAndEliminadoFalse(
                    nombreArchivo, usuario);
                
                if (!archivosEncontrados.isEmpty()) {
                    // Encontrado, actualizar su directorio
                    Archivo archivo = archivosEncontrados.get(0);
                    archivo.setDirectorio(directorio);
                    archivoRepository.save(archivo);
                    System.out.println("Archivo " + archivo.getId() + " (" + archivo.getNombre() + ") movido al directorio " + 
                                      directorio.getId() + " (" + directorio.getNombre() + ")");
                }
                // Si no existe, no podemos hacer nada (no creamos archivos nuevos)
            }
        }
        
        // No se eliminan archivos, solo se dejarían sin directorio si fuera necesario
        // Pero según el prompt, nunca se deben eliminar archivos
    }
}