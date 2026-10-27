package pe.edu.upeu.eduspace.examenes.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.examenes.entity.ExamenPregunta;

import java.util.List;

@Repository
public interface ExamenPreguntaRepository extends JpaRepository<ExamenPregunta, Long> {

    List<ExamenPregunta> findByExamenIdOrderByOrdenAsc(Long examenId);

    void deleteByExamenId(Long examenId);
}

