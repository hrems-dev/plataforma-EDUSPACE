package pe.edu.eduspace.alumnos.service;

import java.io.InputStream;
import pe.edu.eduspace.alumnos.dto.ImportacionResponse;

public interface ImportacionAlumnosService {

    /**
     * Crea alumnos (con un apoderado cada uno) desde un CSV UTF-8. Encabezados esperados:
     * {@code tipoDocumento,numeroDocumento,nombres,apellidoPaterno,apellidoMaterno,fechaNacimiento,sexo,direccion,distrito,
     * telefono,email,apoderadoTipoDocumento,apoderadoNumeroDocumento,apoderadoNombres,apoderadoApellidos,
     * apoderadoParentesco,apoderadoTelefono,apoderadoEmail}.
     */
    ImportacionResponse importar(InputStream csv);
}
