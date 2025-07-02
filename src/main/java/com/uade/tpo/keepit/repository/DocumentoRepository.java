package com.uade.tpo.keepit.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.uade.tpo.keepit.entities.Documento;
import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface DocumentoRepository extends JpaRepository<Documento, Long> {
    // Aquí puedes definir métodos específicos para manejar documentos
    // Por ejemplo, encontrar documentos por usuario, por nombre, etc.
    // List<Documento> findByUsuarioAndEliminadoFalse(Usuario usuario);
    // Optional<Documento> findByIdAndUsuarioAndEliminadoFalse(Long id, Usuario usuario);
    // List<Documento> findByNombreAndUsuarioAndEliminadoFalse(String nombre, Usuario usuario);
    List<Documento> findByFechaVencimientoBetween(LocalDate now, LocalDate plusDays);
}
