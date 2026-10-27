package pe.edu.eduspace.planificacion.repository;

import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.eduspace.planificacion.entity.BloqueHorario;
import pe.edu.eduspace.planificacion.entity.Turno;

public interface BloqueHorarioRepository extends JpaRepository<BloqueHorario, Long> {
    List<BloqueHorario> findByTurnoOrderByOrden(Turno turno);

    List<BloqueHorario> findAllByOrderByTurnoAscOrdenAsc();

    boolean existsByTurnoAndOrden(Turno turno, Short orden);

    boolean existsByTurnoAndOrdenAndIdNot(Turno turno, Short orden, Long id);

    @Query("""
            select count(b) > 0 from BloqueHorario b
            where b.turno = :turno and (:excluirId is null or b.id <> :excluirId)
              and b.horaInicio < :fin and b.horaFin > :inicio
            """)
    boolean haySolapamiento(@Param("turno") Turno turno, @Param("inicio") LocalTime inicio,
                            @Param("fin") LocalTime fin, @Param("excluirId") Long excluirId);
}
