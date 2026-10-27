package pe.edu.upeu.eduspace.pago.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConceptoRequest {

    @NotBlank(message = "El código es obligatorio")
    @Size(max = 30)
    private String codigo;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100)
    private String nombre;

    @NotBlank(message = "El tipo es obligatorio")
    @Pattern(regexp = "MATRICULA|PENSION|OTRO", message = "Tipo debe ser MATRICULA, PENSION u OTRO")
    private String tipo;

    private Boolean activo = true;
}

