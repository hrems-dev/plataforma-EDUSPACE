package pe.edu.eduspace.alumnos.dto;

import java.time.LocalDateTime;

public record SaludResponse(Long alumnoId, String grupoSanguineo, String alergias, String observaciones,
                            String seguroMedico, String contactoEmergenciaNombre, String contactoEmergenciaTelefono,
                            LocalDateTime actualizadoEn) {
}
