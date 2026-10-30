package pe.edu.upeu.eduspace.matricula.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.matricula.client.PlanificacionClient;
import pe.edu.upeu.eduspace.matricula.client.PlanificacionGateway;
import pe.edu.upeu.eduspace.matricula.dto.Dtos.CupoResponse;
import pe.edu.upeu.eduspace.matricula.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.matricula.repository.CupoSeccionRepository;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CupoService {

    private final CupoSeccionRepository cupoRepository;
    private final PlanificacionGateway planificacion;

    /** Crea o sincroniza la fila de cupo de la sección la primera vez que se usa. */
    @Transactional
    public void asegurar(PlanificacionClient.Seccion s) {
        short capacidad = s.capacidad() == null ? 0 : s.capacidad();
        if (capacidad <= 0) {
            throw new BusinessRuleException("La sección " + s.nombre() + " no tiene capacidad configurada en planificación.");
        }
        cupoRepository.insertarSiNoExiste(s.id(), s.anioId(), s.nivelId(), s.gradoId(), s.nombre(), capacidad);
        cupoRepository.ajustarCapacidad(s.id(), capacidad);
    }

    /** Reserva atómica de un cupo; sin cupo → 409. Debe ejecutarse dentro de la transacción de la matrícula. */
    @Transactional
    public void reservar(Long seccionId) {
        if (cupoRepository.reservar(seccionId) == 0) {
            throw new BusinessRuleException("La sección no tiene cupos disponibles.");
        }
    }

    @Transactional
    public void liberar(Long seccionId) {
        if (cupoRepository.liberar(seccionId) == 0) {
            log.warn("No se pudo liberar cupo de la sección {} (ya estaba en 0)", seccionId);
        }
    }

    /** Vacantes por grado: sincroniza con planificación las secciones del grado y devuelve su ocupación. */
    @Transactional
    public List<CupoResponse> vacantes(Long anioId, Long gradoId) {
        List<PlanificacionClient.Seccion> vigentes = planificacion.secciones(anioId, gradoId);
        for (PlanificacionClient.Seccion s : vigentes) {
            try {
                asegurar(s);
            } catch (BusinessRuleException e) {
                log.warn("Sección {} omitida de vacantes: {}", s.id(), e.getMessage());
            }
        }
        List<CupoResponse> res = new ArrayList<>();
        var cupos = gradoId != null
            ? cupoRepository.findByAnioIdAndGradoIdOrderBySeccionNombre(anioId, gradoId)
            : cupoRepository.findByAnioIdOrderByGradoIdAscSeccionNombreAsc(anioId);
        // Una sección eliminada en planificación deja su fila de cupo aquí: no se muestra. Si planificación no devolvió
        // secciones (caída o año vacío) no se puede afirmar nada y se devuelve lo que hay.
        var ids = vigentes.stream().map(PlanificacionClient.Seccion::id).collect(java.util.stream.Collectors.toSet());
        cupos.stream().filter(c -> ids.isEmpty() || ids.contains(c.getSeccionId())).forEach(c -> res.add(MatriculaMapper.toResponse(c)));
        return res;
    }
}
