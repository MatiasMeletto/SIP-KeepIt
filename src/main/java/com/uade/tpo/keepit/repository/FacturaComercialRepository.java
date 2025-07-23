package com.uade.tpo.keepit.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.uade.tpo.keepit.entities.FacturaComercial;
import com.uade.tpo.keepit.entities.Usuario;

@Repository
public interface FacturaComercialRepository extends JpaRepository<FacturaComercial, Long> {
    
    /**
     * Encuentra facturas con fecha de vencimiento entre las fechas especificadas
     */
    @Query("SELECT f FROM FacturaComercial f WHERE f.fechaVencimiento BETWEEN :fechaInicio AND :fechaFin AND f.eliminado = false")
    List<FacturaComercial> findFacturasConVencimientoCercano(
            @Param("fechaInicio") LocalDate fechaInicio, 
            @Param("fechaFin") LocalDate fechaFin);
    
    /**
     * Encuentra facturas de un usuario específico con vencimiento cercano
     */
    @Query("SELECT f FROM FacturaComercial f WHERE f.fechaVencimiento BETWEEN :fechaInicio AND :fechaFin " +
           "AND f.usuario = :usuario AND f.eliminado = false")
    List<FacturaComercial> findFacturasConVencimientoCercanoPorUsuario(
            @Param("fechaInicio") LocalDate fechaInicio, 
            @Param("fechaFin") LocalDate fechaFin,
            @Param("usuario") Usuario usuario);
    
    /**
     * Encuentra facturas por fecha de vencimiento específica
     */
    List<FacturaComercial> findByFechaVencimientoAndEliminadoFalse(LocalDate fechaVencimiento);
    
    /**
     * Encuentra facturas que vencen hoy para un usuario
     */
    @Query("SELECT f FROM FacturaComercial f WHERE f.fechaVencimiento = CURRENT_DATE " +
           "AND f.usuario = :usuario AND f.eliminado = false")
    List<FacturaComercial> findFacturasQueVencenHoyPorUsuario(@Param("usuario") Usuario usuario);
    
    /**
     * Encuentra facturas vencidas para un usuario
     */
    @Query("SELECT f FROM FacturaComercial f WHERE f.fechaVencimiento < CURRENT_DATE " +
           "AND f.usuario = :usuario AND f.eliminado = false")
    List<FacturaComercial> findFacturasVencidasPorUsuario(@Param("usuario") Usuario usuario);
    
    /**
     * Encuentra facturas que vencen hoy
     */
    @Query("SELECT f FROM FacturaComercial f WHERE f.fechaVencimiento = CURRENT_DATE AND f.eliminado = false")
    List<FacturaComercial> findFacturasQueVencenHoy();
    
    /**
     * Encuentra facturas vencidas
     */
    @Query("SELECT f FROM FacturaComercial f WHERE f.fechaVencimiento < CURRENT_DATE AND f.eliminado = false")
    List<FacturaComercial> findFacturasVencidas();
}
