package com.uade.tpo.keepit.model;

import com.uade.tpo.keepit.entities.Directorio;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClasificacionResult {
    private boolean exitoso;
    private String carpeta;
    private String textoExtraido;
    private Directorio directorio;
    private ExtraccionAtributos atributos;
    private List<String> camposFaltantes;
}