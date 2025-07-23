package com.uade.tpo.keepit.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.uade.tpo.keepit.entities.Documento;
import com.uade.tpo.keepit.entities.Usuario;

@Repository
public interface DocumentoRepository extends JpaRepository<Documento, Long> {
    
    /**
     * Encuentra documentos con fecha de vencimiento entre las fechas especificadas
     */
    List<Documento> findByFechaVencimientoBetween(LocalDate fechaInicio, LocalDate fechaFin);
    
    /**
     * Encuentra documentos de un usuario específico con vencimiento cercano
     */
    @Query("SELECT d FROM Documento d WHERE d.fechaVencimiento BETWEEN :fechaInicio AND :fechaFin " +
           "AND d.usuario = :usuario AND d.eliminado = false")
    List<Documento> findByFechaVencimientoBetweenAndUsuario(
            @Param("fechaInicio") LocalDate fechaInicio, 
            @Param("fechaFin") LocalDate fechaFin,
            @Param("usuario") Usuario usuario);
    
    /**
     * Encuentra documentos que vencen hoy para un usuario
     */
    @Query("SELECT d FROM Documento d WHERE d.fechaVencimiento = CURRENT_DATE " +
           "AND d.usuario = :usuario AND d.eliminado = false")
    List<Documento> findDocumentosQueVencenHoyPorUsuario(@Param("usuario") Usuario usuario);
    
    /**
     * Encuentra documentos vencidos para un usuario
     */
    @Query("SELECT d FROM Documento d WHERE d.fechaVencimiento < CURRENT_DATE " +
           "AND d.usuario = :usuario AND d.eliminado = false")
    List<Documento> findDocumentosVencidosPorUsuario(@Param("usuario") Usuario usuario);
}
