package pe.edu.eduspace.alumnos.dto;

import jakarta.validation.constraints.Size;

public record SaludRequest(
        @Size(max = 5) String grupoSanguineo,
        @Size(max = 300) String alergias,
        @Size(max = 500) String observaciones,
        @Size(max = 100) String seguroMedico,
        @Size(max = 150) String contactoEmergenciaNombre,
        @Size(max = 20) String contactoEmergenciaTelefono) {
}
