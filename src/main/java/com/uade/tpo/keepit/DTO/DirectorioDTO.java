package com.uade.tpo.keepit.DTO;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectorioDTO {
    private String id_directorio;
    private String nombre;
    private String id_directorio_padre;
    private List<String> archivos = new ArrayList<>();
}