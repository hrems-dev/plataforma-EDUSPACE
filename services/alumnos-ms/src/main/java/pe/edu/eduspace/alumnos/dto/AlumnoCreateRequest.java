package pe.edu.eduspace.alumnos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record AlumnoCreateRequest(
        @Size(max = 10) String tipoDocumento,
        @NotBlank @Size(max = 20) String numeroDocumento,
        @NotBlank @Size(max = 100) String nombres,
        @NotBlank @Size(max = 80) String apellidoPaterno,
        @Size(max = 80) String apellidoMaterno,
        @NotNull @Past(message = "la fecha de nacimiento no puede ser futura") LocalDate fechaNacimiento,
        @NotBlank @Pattern(regexp = "[MF]", message = "debe ser M o F") String sexo,
        @Size(max = 50) String nacionalidad,
        @Size(max = 200) String direccion,
        @Size(max = 80) String distrito,
        @Size(max = 20) String telefono,
        @Email @Size(max = 150) String email,
        @Size(max = 150) String colegioProcedencia,
        @NotEmpty(message = "el alumno necesita al menos un apoderado") List<@Valid VinculoApoderadoRequest> apoderados) {
}
