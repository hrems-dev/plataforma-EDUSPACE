package pe.edu.upeu.eduspace.pago.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatriculaEvent {

    private Long matriculaId;
    private Long alumnoId;
    private String alumnoNombre;
    private Long anioId;
    private Long nivelId;
    private Long gradoId;
    private Long seccionId;
    private String estado;
    private String tipo;
    private Long seccionAnteriorId;
    private String motivo;
    private Long responsablePagoUsuarioId;
    private String responsablePagoNombre;
}

