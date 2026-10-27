package pe.edu.upeu.eduspace.examenes.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.examenes.entity.OpcionPregunta;

import java.util.List;

@Repository
public interface OpcionPreguntaRepository extends JpaRepository<OpcionPregunta, Long> {

    List<OpcionPregunta> findByPreguntaIdOrderByOrdenAsc(Long preguntaId);
}

