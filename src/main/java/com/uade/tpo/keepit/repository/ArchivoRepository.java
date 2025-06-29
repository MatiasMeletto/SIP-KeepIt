package com.uade.tpo.keepit.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.uade.tpo.keepit.entities.Archivo;
import com.uade.tpo.keepit.entities.Usuario;

@Repository
public interface ArchivoRepository extends JpaRepository<Archivo, Long> {
    List<Archivo> findByUsuarioAndEliminadoFalse(Usuario usuario);
    Optional<Archivo> findByIdAndUsuarioAndEliminadoFalse(Long id, Usuario usuario);
    List<Archivo> findByNombreAndUsuarioAndEliminadoFalse(String nombre, Usuario usuario);
    List<Archivo> findByFechaVencimientoBetween(LocalDate now, LocalDate plusDays);
}
