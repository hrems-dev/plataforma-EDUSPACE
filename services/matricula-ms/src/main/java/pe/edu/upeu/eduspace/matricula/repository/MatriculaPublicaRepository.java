package pe.edu.upeu.eduspace.matricula.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upeu.eduspace.matricula.entity.MatriculaPublica;

public interface MatriculaPublicaRepository extends JpaRepository<MatriculaPublica, Long> {

    /** Último intento de matrícula en línea de un alumno (para retomar una reserva que venció sin pago). */
    java.util.Optional<MatriculaPublica> findFirstByAlumnoDocumentoAndEstadoOrderByCreadoEnDesc(String alumnoDocumento, String estado);
}
