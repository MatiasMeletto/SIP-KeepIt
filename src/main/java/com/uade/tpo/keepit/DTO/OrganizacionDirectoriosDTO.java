package com.uade.tpo.keepit.DTO;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrganizacionDirectoriosDTO {
    private List<DirectorioDTO> directorios;
    private String explicacion;
}