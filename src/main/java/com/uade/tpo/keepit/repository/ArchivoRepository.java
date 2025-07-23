package com.uade.tpo.keepit.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.uade.tpo.keepit.entities.Archivo;
import com.uade.tpo.keepit.entities.Directorio;
import com.uade.tpo.keepit.entities.Usuario;

@Repository
public interface ArchivoRepository extends JpaRepository<Archivo, Long> {
    List<Archivo> findByUsuarioAndEliminadoFalse(Usuario usuario);
    Optional<Archivo> findByIdAndUsuarioAndEliminadoFalse(Long id, Usuario usuario);
    List<Archivo> findByNombreAndUsuarioAndEliminadoFalse(String nombre, Usuario usuario);
    boolean existsByNombreAndUsuarioAndEliminadoFalse(String nombre, Usuario usuario);
    Long countByUsuarioAndEliminadoFalse(Usuario usuario);
    
    @Query("SELECT COUNT(a) FROM Archivo a WHERE a.directorio = :directorio AND a.eliminado = false")
    Long countByDirectorioAndEliminadoFalse(@Param("directorio") Directorio directorio);
}
