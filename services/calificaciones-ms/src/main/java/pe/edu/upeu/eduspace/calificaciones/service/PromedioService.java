package pe.edu.upeu.eduspace.calificaciones.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.calificaciones.client.PlanificacionClient;
import pe.edu.upeu.eduspace.calificaciones.dto.PromedioResponse;
import pe.edu.upeu.eduspace.calificaciones.entity.CriterioEvaluacion;
import pe.edu.upeu.eduspace.calificaciones.entity.EscalaLiteral;
import pe.edu.upeu.eduspace.calificaciones.entity.MatriculaLocal;
import pe.edu.upeu.eduspace.calificaciones.entity.Nota;
import pe.edu.upeu.eduspace.calificaciones.entity.PromedioPeriodo;
import pe.edu.upeu.eduspace.calificaciones.repository.CriterioEvaluacionRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.EscalaLiteralRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.MatriculaLocalRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.NotaRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.PromedioPeriodoRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromedioService {

    private final PromedioPeriodoRepository promedioRepository;
    private final CriterioEvaluacionRepository criterioRepository;
    private final NotaRepository notaRepository;
    private final EscalaLiteralRepository escalaLiteralRepository;
    private final MatriculaLocalRepository matriculaLocalRepository;
    private final PlanificacionClient planificacionClient;

    private static final String NIVEL_POR_DEFECTO = "PRIMARIA";
    private final Map<Long, String> codigosNivel = new ConcurrentHashMap<>();

    /** Código del nivel de la matrícula (INICIAL, PRIMARIA…); si no se puede resolver, la escala PRIMARIA. */
    public String nivelCodigo(MatriculaLocal matricula) {
        if (matricula == null || matricula.getNivelId() == null) return NIVEL_POR_DEFECTO;
        Long nivelId = matricula.getNivelId();
        String cacheado = codigosNivel.get(nivelId);
        if (cacheado != null) return cacheado;
        try {
            Object codigo = planificacionClient.getNivelPorId(nivelId).get("codigo");
            if (codigo != null) {
                codigosNivel.put(nivelId, codigo.toString());
                return codigo.toString();
            }
        } catch (Exception e) {
            log.warn("No se pudo resolver el nivel {}: se usa la escala {}", nivelId, NIVEL_POR_DEFECTO);
        }
        return NIVEL_POR_DEFECTO;
    }

    @Transactional
    public PromedioPeriodo calcularYGuardarPromedio(Long matriculaId, Long cursoId, Long periodoId) {
        MatriculaLocal matricula = matriculaLocalRepository.findById(matriculaId).orElse(null);
        Long seccionId = matricula != null ? matricula.getSeccionId() : 0L;
        Long alumnoId = matricula != null ? matricula.getAlumnoId() : 0L;

        List<CriterioEvaluacion> criterios = criterioRepository.findByPeriodoIdAndSeccionIdAndCursoId(periodoId, seccionId, cursoId);
        if (criterios.isEmpty()) {
            return null;
        }

        BigDecimal sumaPonderada = BigDecimal.ZERO;
        BigDecimal sumaPesosEvaluados = BigDecimal.ZERO;

        for (CriterioEvaluacion criterio : criterios) {
            List<Nota> notasCriterio = notaRepository.findByMatriculaIdAndCriterioId(matriculaId, criterio.getId());
            if (!notasCriterio.isEmpty()) {
                // Si hay varias notas (ej: múltiples tareas o exámenes bajo este criterio), promedio simple
                BigDecimal promedioCriterio = notasCriterio.stream()
                    .map(Nota::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(notasCriterio.size()), 4, RoundingMode.HALF_UP);

                BigDecimal ponderacion = promedioCriterio.multiply(criterio.getPeso())
                    .divide(new BigDecimal("100.00"), 4, RoundingMode.HALF_UP);

                sumaPonderada = sumaPonderada.add(ponderacion);
                sumaPesosEvaluados = sumaPesosEvaluados.add(criterio.getPeso());
            }
        }

        // Si la suma de pesos evaluados es mayor a 0 pero no llega a 100 en borrador, normalizar o mantener según diseño
        // Regla §3.4: "Si un criterio no tiene notas todavía, se calcula con los criterios disponibles y el promedio queda BORRADOR"
        BigDecimal promedioFinal;
        if (sumaPesosEvaluados.compareTo(BigDecimal.ZERO) > 0) {
            if (sumaPesosEvaluados.compareTo(new BigDecimal("100.00")) == 0) {
                promedioFinal = sumaPonderada.setScale(2, RoundingMode.HALF_UP);
            } else {
                // Normalizado proporcional a los pesos evaluados para estimación borrador
                promedioFinal = sumaPonderada.multiply(new BigDecimal("100.00"))
                    .divide(sumaPesosEvaluados, 2, RoundingMode.HALF_UP);
            }
        } else {
            promedioFinal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        String literal = obtenerLiteral(promedioFinal, nivelCodigo(matricula));

        PromedioPeriodo promedio = promedioRepository.findByMatriculaIdAndCursoIdAndPeriodoId(matriculaId, cursoId, periodoId)
            .orElseGet(() -> PromedioPeriodo.builder()
                .matriculaId(matriculaId)
                .alumnoId(alumnoId)
                .seccionId(seccionId)
                .cursoId(cursoId)
                .periodoId(periodoId)
                .estado("BORRADOR")
                .build());

        promedio.setPromedio(promedioFinal);
        promedio.setLiteral(literal);
        promedio.setCalculadoEn(LocalDateTime.now());

        PromedioPeriodo guardado = promedioRepository.save(promedio);
        log.debug("Promedio calculado: matricula={}, curso={}, periodo={}, promedio={}, literal={}",
            matriculaId, cursoId, periodoId, promedioFinal, literal);
        return guardado;
    }

    public String obtenerLiteral(BigDecimal nota, String nivelCodigo) {
        if (nota == null) return null;
        return escalaLiteralRepository.findByNivelCodigoAndNota(nivelCodigo, nota)
            .or(() -> NIVEL_POR_DEFECTO.equals(nivelCodigo) ? java.util.Optional.empty()
                : escalaLiteralRepository.findByNivelCodigoAndNota(NIVEL_POR_DEFECTO, nota))
            .map(EscalaLiteral::getLiteral)
            .orElseGet(() -> {
                double val = nota.doubleValue();
                if (val >= 18.0) return "AD";
                if (val >= 14.0) return "A";
                if (val >= 11.0) return "B";
                return "C";
            });
    }

    public PromedioResponse toResponse(PromedioPeriodo p, String alumnoNombre) {
        return PromedioResponse.builder()
            .id(p.getId())
            .matriculaId(p.getMatriculaId())
            .alumnoId(p.getAlumnoId())
            .alumnoNombre(alumnoNombre)
            .seccionId(p.getSeccionId())
            .cursoId(p.getCursoId())
            .periodoId(p.getPeriodoId())
            .promedio(p.getPromedio())
            .literal(p.getLiteral())
            .estado(p.getEstado())
            .calculadoEn(p.getCalculadoEn())
            .build();
    }
}

