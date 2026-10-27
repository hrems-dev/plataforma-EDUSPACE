package pe.edu.upeu.eduspace.examenes.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.upeu.eduspace.examenes.entity.Respuesta;

import java.util.List;
import java.util.Optional;

@Repository
public interface RespuestaRepository extends JpaRepository<Respuesta, Long> {

    Optional<Respuesta> findByIntentoIdAndPreguntaId(Long intentoId, Long preguntaId);

    List<Respuesta> findByIntentoId(Long intentoId);
}

