package pe.edu.eduspace.alumnos.dto;

import java.time.LocalDate;
import java.util.List;
import pe.edu.eduspace.alumnos.entity.EstadoAlumno;

public record AlumnoResponse(Long id, String codigo, Long usuarioId, String tipoDocumento, String numeroDocumento,
                             String nombres, String apellidoPaterno, String apellidoMaterno, String nombreCompleto,
                             LocalDate fechaNacimiento, String sexo, String nacionalidad, String direccion,
                             String distrito, String telefono, String email, String fotoUrl,
                             String colegioProcedencia, EstadoAlumno estado, List<VinculoResponse> apoderados) {
}
