package pe.edu.upeu.eduspace.pago.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Espejo del contrato interno de alumnos-ms: GET /internal/alumnos/{id}/responsable-pago. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlumnoContactoResponse {

    private Long apoderadoId;
    private Long usuarioId;
    private String nombre;

    /** Espejo de HijoResponse (GET /internal/apoderados/usuario/{id}/alumnos). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HijoResumen {
        private Long alumnoId;
        private String codigo;
        private String nombreCompleto;
        private String estado;
        private String parentesco;
    }
}
