package pe.edu.upeu.eduspace.pago.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConceptoResponse {
    private Long id;
    private String codigo;
    private String nombre;
    private String tipo;
    private Boolean activo;
}

