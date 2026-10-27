package pe.edu.upeu.eduspace.examenes.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsignacionDocenteResponse {

    private Long asignacionId;
    private Long docenteId;
    private Long seccionId;
    private Long cursoId;
    private String cursoNombre;
}

