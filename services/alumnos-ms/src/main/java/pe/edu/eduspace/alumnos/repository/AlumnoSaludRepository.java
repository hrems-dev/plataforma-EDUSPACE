package pe.edu.eduspace.alumnos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.eduspace.alumnos.entity.AlumnoSalud;

public interface AlumnoSaludRepository extends JpaRepository<AlumnoSalud, Long> {
}
