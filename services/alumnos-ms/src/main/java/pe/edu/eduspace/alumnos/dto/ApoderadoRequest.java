package pe.edu.eduspace.alumnos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApoderadoRequest(
        @Size(max = 10) String tipoDocumento,
        @NotBlank @Size(max = 20) String numeroDocumento,
        @NotBlank @Size(max = 100) String nombres,
        @NotBlank @Size(max = 150) String apellidos,
        @Size(max = 20) String telefono,
        @Size(max = 20) String telefonoAlterno,
        @Email @Size(max = 150) String email,
        @Size(max = 200) String direccion,
        @Size(max = 100) String ocupacion) {
}
