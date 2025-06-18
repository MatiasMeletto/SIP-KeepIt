package com.uade.tpo.keepit.repository;

import com.uade.tpo.keepit.entities.Directorio;
import com.uade.tpo.keepit.entities.Usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DirectorioRepository extends JpaRepository<Directorio, Long> {
    Optional<Directorio> findByNombreAndPadreId(String nombre, Long padreId);
    
    Optional<Directorio> findByNombreAndPadreIsNull(String nombre);
    
    List<Directorio> findAllByPadreIsNull();

    @Query("SELECT d FROM Directorio d WHERE d.usuario IS NULL OR d.usuario.id = :usuarioId")
    List<Directorio> findAllByUsuarioId(Long usuarioId);
    
    @Query("SELECT d FROM Directorio d WHERE d.padre IS NULL")
    List<Directorio> findRootDirectories();
    
    @Query("SELECT COUNT(a) FROM Archivo a WHERE a.directorio.id = :directorioId")
    int countArchivosByDirectorioId(Long directorioId);
}