package com.uade.tpo.keepit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.uade.tpo.keepit.entities.Archivo;

@Repository
public interface ArchivoRepository extends JpaRepository<Archivo, Long> {

}
