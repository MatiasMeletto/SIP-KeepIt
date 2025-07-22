package com.uade.tpo.keepit.repository;

import com.uade.tpo.keepit.entities.Directorio;
import com.uade.tpo.keepit.entities.Usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DirectorioRepository extends JpaRepository<Directorio, Long> {
    Optional<Directorio> findByNombreAndPadreId(String nombre, Long padreId);
    
    Optional<Directorio> findByNombreAndPadreIsNull(String nombre);

    List<Directorio> findByUsuario(Usuario usuario);
    
    // Versiones que consideran solo directorios no eliminados
    List<Directorio> findByUsuarioAndEliminadoFalse(Usuario usuario);
    
    Optional<Directorio> findByNombreAndPadreIdAndEliminadoFalse(String nombre, Long padreId);
    
    Optional<Directorio> findByNombreAndPadreIsNullAndEliminadoFalse(String nombre);
    
    List<Directorio> findAllByPadreIsNull();

    @Query("SELECT d FROM Directorio d WHERE d.usuario IS NULL OR d.usuario.id = :usuarioId")
    List<Directorio> findAllByUsuarioId(Long usuarioId);
    
    @Query("SELECT d FROM Directorio d WHERE (d.usuario IS NULL OR d.usuario.id = :usuarioId) AND d.eliminado = false")
    List<Directorio> findAllByUsuarioIdAndEliminadoFalse(@Param("usuarioId") Long usuarioId);

    @Query("SELECT d FROM Directorio d WHERE d.padre IS NULL")
    List<Directorio> findRootDirectories();
    
    @Query("SELECT d FROM Directorio d WHERE d.padre IS NULL AND d.eliminado = false")
    List<Directorio> findRootDirectoriesAndEliminadoFalse();

    @Query("SELECT COUNT(a) FROM Archivo a WHERE a.directorio.id = :directorioId")
    int countArchivosByDirectorioId(Long directorioId);

    @Query("SELECT d FROM Directorio d WHERE d.usuario.id = :usuarioId AND d.padre IS NULL")
    List<Directorio> findByUsuarioIdAndPadreIsNull(Long usuarioId);
    
    @Query("SELECT d FROM Directorio d WHERE d.usuario.id = :usuarioId AND d.padre IS NULL AND d.eliminado = false")
    List<Directorio> findByUsuarioIdAndPadreIsNullAndEliminadoFalse(@Param("usuarioId") Long usuarioId);

    @Query("SELECT d FROM Directorio d WHERE d.nombre = :nombre AND d.padre IS NULL AND d.usuario IS NULL")
    Optional<Directorio> findByNombreAndPadreIsNullAndUsuarioIsNull(String nombre);
    
    @Query("SELECT COUNT(d) FROM Directorio d WHERE d.padre = :padre")
    Long countByPadre(@Param("padre") Directorio padre);
    
    @Query("SELECT COUNT(d) FROM Directorio d WHERE d.padre = :padre AND d.eliminado = false")
    Long countByPadreAndEliminadoFalse(@Param("padre") Directorio padre);
}