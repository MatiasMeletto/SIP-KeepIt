package com.uade.tpo.keepit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtraccionAtributos {
    private String fecha;
    private Double monto;
    private String emisor;
    private String concepto;
    private String numeroDocumento;
    private String tipoDocumento;
    private String receptor;
}