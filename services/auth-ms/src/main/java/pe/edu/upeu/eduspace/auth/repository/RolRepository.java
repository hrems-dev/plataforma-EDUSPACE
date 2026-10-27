package pe.edu.upeu.eduspace.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upeu.eduspace.auth.entity.Rol;

import java.util.Collection;
import java.util.List;

public interface RolRepository extends JpaRepository<Rol, Long> {

    List<Rol> findByNombreIn(Collection<String> nombres);
}
