package pe.edu.upeu.eduspace.examenes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcionRequest {

    @NotBlank(message = "El texto de la opción es obligatorio")
    @Size(max = 500)
    private String texto;

    @Builder.Default
    private Boolean esCorrecta = false;
    @Builder.Default
    private Short orden = 1;
}
