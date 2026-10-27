package pe.edu.eduspace.alumnos.dto;

public record ApoderadoResponse(Long id, Long usuarioId, String tipoDocumento, String numeroDocumento, String nombres,
                                String apellidos, String telefono, String telefonoAlterno, String email,
                                String direccion, String ocupacion) {
}
