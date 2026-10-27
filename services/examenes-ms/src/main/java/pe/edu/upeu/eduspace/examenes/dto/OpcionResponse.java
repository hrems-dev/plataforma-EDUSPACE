package pe.edu.upeu.eduspace.examenes.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpcionResponse {
    private Long id;
    private String texto;
    private Boolean esCorrecta;
    private Short orden;
}

