package pe.edu.upeu.eduspace.calificaciones.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.calificaciones.dto.ResultadoAnualResponse;
import pe.edu.upeu.eduspace.calificaciones.entity.MatriculaLocal;
import pe.edu.upeu.eduspace.calificaciones.entity.PromedioPeriodo;
import pe.edu.upeu.eduspace.calificaciones.entity.ResultadoAnual;
import pe.edu.upeu.eduspace.calificaciones.repository.MatriculaLocalRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.PromedioPeriodoRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.ResultadoAnualRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResultadoAnualService {

    private final ResultadoAnualRepository resultadoAnualRepository;
    private final PromedioPeriodoRepository promedioRepository;
    private final MatriculaLocalRepository matriculaLocalRepository;
    private final PromedioService promedioService;

    @Transactional
    public List<ResultadoAnualResponse> calcularResultadosSeccion(Long seccionId) {
        List<MatriculaLocal> matriculas = matriculaLocalRepository.findBySeccionId(seccionId);
        List<ResultadoAnualResponse> responses = new ArrayList<>();

        for (MatriculaLocal m : matriculas) {
            List<ResultadoAnual> resultados = calcularResultadosMatricula(m);
            responses.addAll(resultados.stream().map(this::toResponse).toList());
        }

        return responses;
    }

    @Transactional
    public List<ResultadoAnual> calcularResultadosMatricula(MatriculaLocal matricula) {
        List<PromedioPeriodo> promedios = promedioRepository.findByMatriculaId(matricula.getMatriculaId());

        // Agrupar por curso
        Map<Long, List<PromedioPeriodo>> porCurso = promedios.stream()
            .collect(Collectors.groupingBy(PromedioPeriodo::getCursoId));

        List<ResultadoAnual> lista = new ArrayList<>();

        for (Map.Entry<Long, List<PromedioPeriodo>> entry : porCurso.entrySet()) {
            Long cursoId = entry.getKey();
            List<PromedioPeriodo> promsCurso = entry.getValue();

            if (promsCurso.isEmpty()) continue;

            // Promedio simple de los periodos
            BigDecimal suma = promsCurso.stream()
                .map(PromedioPeriodo::getPromedio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal promedioFinal = suma.divide(BigDecimal.valueOf(promsCurso.size()), 2, RoundingMode.HALF_UP);

            // Regla de promoción (§3.4):
            // APROBADO: >= 11.00
            // RECUPERACION: >= 8.00 y < 11.00
            // DESAPROBADO: < 8.00
            String estado;
            if (promedioFinal.compareTo(new BigDecimal("11.00")) >= 0) {
                estado = "APROBADO";
            } else if (promedioFinal.compareTo(new BigDecimal("8.00")) >= 0) {
                estado = "RECUPERACION";
            } else {
                estado = "DESAPROBADO";
            }

            String literal = promedioService.obtenerLiteral(promedioFinal, promedioService.nivelCodigo(matricula));

            ResultadoAnual res = resultadoAnualRepository.findByMatriculaIdAndCursoId(matricula.getMatriculaId(), cursoId)
                .orElseGet(() -> ResultadoAnual.builder()
                    .matriculaId(matricula.getMatriculaId())
                    .alumnoId(matricula.getAlumnoId())
                    .anioId(matricula.getAnioId())
                    .cursoId(cursoId)
                    .build());

            res.setPromedioFinal(promedioFinal);
            res.setLiteral(literal);
            res.setEstado(estado);
            res.setCalculadoEn(LocalDateTime.now());

            lista.add(resultadoAnualRepository.save(res));
        }

        return lista;
    }

    public ResultadoAnualResponse toResponse(ResultadoAnual r) {
        return ResultadoAnualResponse.builder()
            .id(r.getId())
            .matriculaId(r.getMatriculaId())
            .alumnoId(r.getAlumnoId())
            .anioId(r.getAnioId())
            .cursoId(r.getCursoId())
            .promedioFinal(r.getPromedioFinal())
            .literal(r.getLiteral())
            .estado(r.getEstado())
            .calculadoEn(r.getCalculadoEn())
            .build();
    }
}

