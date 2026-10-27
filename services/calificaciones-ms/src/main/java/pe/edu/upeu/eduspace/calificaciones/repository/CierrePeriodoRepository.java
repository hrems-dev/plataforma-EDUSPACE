package pe.edu.upeu.eduspace.calificaciones.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.calificaciones.entity.CierrePeriodo;

import java.util.List;
import java.util.Optional;

@Repository
public interface CierrePeriodoRepository extends JpaRepository<CierrePeriodo, Long> {

    Optional<CierrePeriodo> findBySeccionIdAndPeriodoId(Long seccionId, Long periodoId);

    List<CierrePeriodo> findBySeccionId(Long seccionId);
}

