package pe.edu.upeu.eduspace.examenes.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.examenes.dto.ResultadosExamenResponse;
import pe.edu.upeu.eduspace.examenes.entity.Examen;
import pe.edu.upeu.eduspace.examenes.entity.Intento;
import pe.edu.upeu.eduspace.examenes.entity.MatriculaLocal;
import pe.edu.upeu.eduspace.examenes.entity.Respuesta;
import pe.edu.upeu.eduspace.examenes.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.examenes.repository.ExamenRepository;
import pe.edu.upeu.eduspace.examenes.repository.IntentoRepository;
import pe.edu.upeu.eduspace.examenes.repository.MatriculaLocalRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ResultadosService {

    private final ExamenRepository examenRepository;
    private final IntentoRepository intentoRepository;
    private final MatriculaLocalRepository matriculaLocalRepository;

    @Transactional(readOnly = true)
    public ResultadosExamenResponse obtenerEstadisticasExamen(Long examenId) {
        Examen examen = examenRepository.findById(examenId)
            .orElseThrow(() -> new ResourceNotFoundException("Examen no encontrado: " + examenId));

        List<Intento> intentos = intentoRepository.findByExamenId(examenId);
        List<MatriculaLocal> matriculas = matriculaLocalRepository.findBySeccionIdAndEstado(examen.getSeccionId(), "MATRICULADO");
        Map<Long, String> nombresAlumnos = new HashMap<>();
        for (MatriculaLocal m : matriculas) {
            nombresAlumnos.put(m.getAlumnoId(), m.getAlumnoNombre());
        }

        List<ResultadosExamenResponse.IntentoEstudianteResumen> resumenes = new ArrayList<>();
        BigDecimal sumaNotas = BigDecimal.ZERO;
        BigDecimal notaMax = BigDecimal.ZERO;
        BigDecimal notaMin = BigDecimal.valueOf(20);
        int evaluados = 0;

        for (Intento i : intentos) {
            boolean requiereRev = i.getRespuestas().stream().anyMatch(Respuesta::getRequiereRevision);
            if (i.getNota() != null) {
                sumaNotas = sumaNotas.add(i.getNota());
                notaMax = notaMax.max(i.getNota());
                notaMin = notaMin.min(i.getNota());
                evaluados++;
            }

            resumenes.add(ResultadosExamenResponse.IntentoEstudianteResumen.builder()
                .intentoId(i.getId())
                .alumnoId(i.getAlumnoId())
                .alumnoNombre(nombresAlumnos.getOrDefault(i.getAlumnoId(), "Alumno " + i.getAlumnoId()))
                .numeroIntento(i.getNumero())
                .estado(i.getEstado())
                .puntajeObtenido(i.getPuntajeObtenido())
                .nota(i.getNota())
                .requiereRevision(requiereRev)
                .build());
        }

        BigDecimal promedio = evaluados > 0 ?
            sumaNotas.divide(BigDecimal.valueOf(evaluados), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        return ResultadosExamenResponse.builder()
            .examenId(examen.getId())
            .titulo(examen.getTitulo())
            .puntajeTotal(examen.getPuntajeTotal())
            .totalIntentos(intentos.size())
            .alumnosEvaluados(evaluados)
            .promedioNotas(promedio)
            .notaMaxima(evaluados > 0 ? notaMax : BigDecimal.ZERO)
            .notaMinima(evaluados > 0 ? notaMin : BigDecimal.ZERO)
            .intentos(resumenes)
            .build();
    }
}

