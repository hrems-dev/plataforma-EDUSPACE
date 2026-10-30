package pe.edu.upeu.eduspace.matricula.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Contrato interno de alumnos-ms (adenda A3). */
@FeignClient(name = "alumnos-ms")
public interface AlumnosClient {

    @GetMapping("/internal/alumnos/{id}")
    AlumnoBasico alumno(@PathVariable("id") Long id);

    @GetMapping("/internal/alumnos/{id}/expediente-completo")
    Expediente expediente(@PathVariable("id") Long id);

    @GetMapping("/internal/alumnos/{id}/responsable-pago")
    ResponsablePago responsablePago(@PathVariable("id") Long id);

    @GetMapping("/internal/alumnos/usuario/{usuarioId}")
    AlumnoUsuario alumnoDeUsuario(@PathVariable("usuarioId") Long usuarioId);

    @GetMapping("/internal/apoderados/usuario/{usuarioId}/alumnos")
    List<Hijo> hijosDeUsuario(@PathVariable("usuarioId") Long usuarioId);

    /** Alta del alumno con su apoderado (matrícula en línea). 409 si ya existe un alumno con ese documento. */
    @PostMapping("/internal/alumnos")
    AlumnoRegistrado registrar(@RequestBody RegistroAlumno body);

    @PostMapping("/internal/alumnos/{id}/usuario")
    void vincularAlumno(@PathVariable("id") Long id, @RequestBody VinculoUsuario body);

    @PostMapping("/internal/apoderados/{id}/usuario")
    void vincularApoderado(@PathVariable("id") Long id, @RequestBody VinculoUsuario body);

    record AlumnoBasico(Long id, String codigo, String nombreCompleto, String estado) {}

    record Expediente(boolean completo, List<String> faltantes) {}

    record ResponsablePago(Long apoderadoId, Long usuarioId, String nombre) {}

    record AlumnoUsuario(Long alumnoId) {}

    record Hijo(Long alumnoId, String codigo, String nombreCompleto, String estado, String parentesco) {}

    record VinculoUsuario(Long usuarioId) {}

    record RegistroAlumno(String tipoDocumento, String numeroDocumento, String nombres, String apellidoPaterno, String apellidoMaterno,
                          String fechaNacimiento, String sexo, String nacionalidad, String telefono, String email,
                          List<VinculoApoderado> apoderados) {}

    record VinculoApoderado(ApoderadoDatos apoderado, String parentesco, Boolean esPrincipal, Boolean esResponsablePago,
                            Boolean viveConAlumno, Boolean puedeRecoger) {}

    record ApoderadoDatos(String tipoDocumento, String numeroDocumento, String nombres, String apellidos, String telefono, String email) {}

    record AlumnoRegistrado(Long id, String codigo, String nombreCompleto, List<VinculoRegistrado> apoderados) {}

    record VinculoRegistrado(Long id, ApoderadoRegistrado apoderado) {}

    record ApoderadoRegistrado(Long id, Long usuarioId, String numeroDocumento, String nombres, String apellidos, String email) {}
}
