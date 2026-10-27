package pe.edu.eduspace.planificacion.service.impl;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.planificacion.dto.*;
import pe.edu.eduspace.planificacion.entity.*;
import pe.edu.eduspace.planificacion.exception.BusinessRuleException;
import pe.edu.eduspace.planificacion.exception.ResourceNotFoundException;
import pe.edu.eduspace.planificacion.mapper.PlanificacionMapper;
import pe.edu.eduspace.planificacion.repository.*;
import pe.edu.eduspace.planificacion.service.HorarioService;

@Service
@RequiredArgsConstructor
@Transactional
public class HorarioServiceImpl implements HorarioService {

    private static final String[] DIAS = {"", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"};
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final HorarioRepository repository;
    private final AsignacionRepository asignacionRepository;
    private final BloqueHorarioRepository bloqueRepository;
    private final AulaRepository aulaRepository;
    private final SeccionRepository seccionRepository;
    private final DocenteRepository docenteRepository;
    private final GradoCursoRepository mallaRepository;
    private final PlanificacionMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public HorarioSeccionResponse porSeccion(Long seccionId) {
        Seccion seccion = seccion(seccionId);
        List<BloqueResponse> bloques = bloqueRepository.findByTurnoOrderByOrden(seccion.getTurno()).stream()
                .map(mapper::toResponse).toList();
        List<HorarioResponse> clases = repository.findBySeccion(seccionId).stream().map(mapper::toResponse).toList();
        return new HorarioSeccionResponse(seccion.getId(), nombre(seccion), seccion.getTurno(), bloques, clases);
    }

    @Override
    @Transactional(readOnly = true)
    public HorarioDocenteResponse porDocente(Long docenteId) {
        Docente docente = docenteRepository.findById(docenteId)
                .orElseThrow(() -> ResourceNotFoundException.of("Docente", docenteId));
        List<HorarioResponse> clases = repository.findByDocente(docenteId).stream().map(mapper::toResponse).toList();
        return new HorarioDocenteResponse(docente.getId(), docente.nombreCompleto(), clases);
    }

    @Override
    public HorarioResponse crear(HorarioRequest request) {
        Horario horario = new Horario();
        aplicar(horario, request, null);
        return mapper.toResponse(repository.save(horario));
    }

    @Override
    public HorarioResponse mover(Long id, HorarioRequest request) {
        Horario horario = buscar(id);
        aplicar(horario, request, id);
        return mapper.toResponse(repository.save(horario));
    }

    @Override
    public void eliminar(Long id) {
        Horario horario = buscar(id);
        if (horario.getAsignacion().getSeccion().getAnio().getEstado() == EstadoPeriodo.CERRADO) {
            throw new BusinessRuleException("El año está CERRADO (solo lectura)");
        }
        repository.delete(horario);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultadoValidacionHorario validar(Long seccionId) {
        Seccion seccion = seccion(seccionId);
        Set<String> choques = new LinkedHashSet<>();
        for (Horario h : repository.findBySeccion(seccionId)) {
            Asignacion a = h.getAsignacion();
            BloqueHorario b = h.getBloque();
            String donde = "el " + DIAS[h.getDiaSemana()] + " en el bloque " + b.getOrden();
            if (repository.existsChoqueDocente(a.getDocente().getId(), h.getDiaSemana(), b.getId(), h.getId())) {
                choques.add("El docente " + a.getDocente().nombreCompleto() + " tiene dos clases " + donde);
            }
            if (h.getAula() != null && repository.existsChoqueAula(h.getAula().getId(), h.getDiaSemana(), b.getId(), h.getId())) {
                choques.add("El aula " + h.getAula().getCodigo() + " está ocupada dos veces " + donde);
            }
            if (repository.existsChoqueSeccion(seccionId, h.getDiaSemana(), b.getId(), h.getId())) {
                choques.add("La sección " + nombre(seccion) + " tiene dos clases " + donde);
            }
            if (b.getTurno() != seccion.getTurno()) {
                choques.add("El bloque " + b.getOrden() + " (" + b.getTurno() + ") no corresponde al turno de la sección (" + seccion.getTurno() + ")");
            }
            if (h.getAula() != null && !cabe(h.getAula(), seccion)) {
                choques.add("El aula " + h.getAula().getCodigo() + " (capacidad " + h.getAula().getCapacidad()
                        + ") no alcanza para la sección " + nombre(seccion) + " (" + seccion.getCapacidad() + " alumnos)");
            }
            if (Boolean.TRUE.equals(b.getEsReceso())) {
                choques.add("Hay una clase programada en un receso " + donde);
            }
        }
        List<CursoHorasResponse> incompletos = new ArrayList<>();
        for (GradoCurso item : mallaRepository.findByGradoIdOrderByCursoNombre(seccion.getGrado().getId())) {
            var asignacion = asignacionRepository.findBySeccionIdAndCursoId(seccionId, item.getCurso().getId());
            int programadas = asignacion.map(a -> (int) repository.countByAsignacionId(a.getId())).orElse(0);
            int requeridas = item.getHorasSemanales();
            if (asignacion.isEmpty()) {
                incompletos.add(new CursoHorasResponse(item.getCurso().getId(), item.getCurso().getNombre(), requeridas, 0, "SIN_DOCENTE"));
            } else if (programadas < requeridas) {
                incompletos.add(new CursoHorasResponse(item.getCurso().getId(), item.getCurso().getNombre(), requeridas, programadas, "FALTANTE"));
            } else if (programadas > requeridas) {
                incompletos.add(new CursoHorasResponse(item.getCurso().getId(), item.getCurso().getNombre(), requeridas, programadas, "SOBRANTE"));
            }
        }
        return new ResultadoValidacionHorario(choques.isEmpty() && incompletos.isEmpty(), List.copyOf(choques), incompletos);
    }

    /** Valida las siete reglas del horario y asigna los campos a la clase (nueva o existente). */
    private void aplicar(Horario horario, HorarioRequest r, Long excluirId) {
        Asignacion asignacion = asignacionRepository.findById(r.asignacionId())
                .orElseThrow(() -> ResourceNotFoundException.of("Asignación", r.asignacionId()));
        Seccion seccion = asignacion.getSeccion();
        if (seccion.getAnio().getEstado() == EstadoPeriodo.CERRADO) {
            throw new BusinessRuleException("El año " + seccion.getAnio().getNombre() + " está CERRADO (solo lectura)");
        }
        BloqueHorario bloque = bloqueRepository.findById(r.bloqueId())
                .orElseThrow(() -> ResourceNotFoundException.of("Bloque horario", r.bloqueId()));
        Aula aula = null;
        Long aulaId = r.aulaId() != null ? r.aulaId() : (seccion.getAula() == null ? null : seccion.getAula().getId());
        if (aulaId != null) {
            aula = aulaRepository.findById(aulaId).orElseThrow(() -> ResourceNotFoundException.of("Aula", aulaId));
            if (!Boolean.TRUE.equals(aula.getActivo())) {
                throw new BusinessRuleException("El aula " + aula.getCodigo() + " está inactiva");
            }
        }
        String slot = "el " + DIAS[r.diaSemana()] + " en el bloque " + bloque.getOrden()
                + " (" + hora(bloque.getHoraInicio()) + "-" + hora(bloque.getHoraFin()) + ")";
        Docente docente = asignacion.getDocente();

        // (1) docente libre
        if (repository.existsChoqueDocente(docente.getId(), r.diaSemana(), bloque.getId(), excluirId)) {
            throw new BusinessRuleException("El docente " + docente.nombreCompleto() + " ya tiene clase " + slot);
        }
        // (2) aula libre
        if (aula != null && repository.existsChoqueAula(aula.getId(), r.diaSemana(), bloque.getId(), excluirId)) {
            throw new BusinessRuleException("El aula " + aula.getCodigo() + " ya está ocupada " + slot);
        }
        // (3) sección sin otra clase
        if (repository.existsChoqueSeccion(seccion.getId(), r.diaSemana(), bloque.getId(), excluirId)) {
            throw new BusinessRuleException("La sección " + nombre(seccion) + " ya tiene otra clase " + slot);
        }
        // (4) bloque del mismo turno que la sección
        if (bloque.getTurno() != seccion.getTurno()) {
            throw new BusinessRuleException("El bloque " + bloque.getOrden() + " es del turno " + bloque.getTurno()
                    + " y la sección " + nombre(seccion) + " es del turno " + seccion.getTurno());
        }
        // (5) no es receso
        if (Boolean.TRUE.equals(bloque.getEsReceso())) {
            throw new BusinessRuleException("El bloque " + bloque.getOrden() + " es un receso: no admite clases");
        }
        // (7) el aula tiene aforo para la sección
        if (aula != null && !cabe(aula, seccion)) {
            throw new BusinessRuleException("El aula " + aula.getCodigo() + " (capacidad " + aula.getCapacidad()
                    + ") no alcanza para la sección " + nombre(seccion) + " (" + seccion.getCapacidad() + " alumnos)");
        }
        // (6) no excede las horas semanales de la malla
        GradoCurso malla = mallaRepository.findByGradoIdAndCursoId(seccion.getGrado().getId(), asignacion.getCurso().getId())
                .orElseThrow(() -> new BusinessRuleException("El curso " + asignacion.getCurso().getNombre()
                        + " ya no está en la malla de " + seccion.getGrado().getNombre()));
        long actuales = repository.countByAsignacionExcluyendo(asignacion.getId(), excluirId);
        if (actuales + 1 > malla.getHorasSemanales()) {
            throw new BusinessRuleException("El curso " + asignacion.getCurso().getNombre() + " ya tiene sus "
                    + malla.getHorasSemanales() + " horas semanales programadas");
        }

        horario.setAsignacion(asignacion);
        horario.setBloque(bloque);
        horario.setAula(aula);
        horario.setDiaSemana(r.diaSemana());
    }

    /** Sin capacidad declarada en el aula no se puede afirmar que no alcance. */
    private boolean cabe(Aula aula, Seccion seccion) {
        return aula.getCapacidad() == null || seccion.getCapacidad() == null || aula.getCapacidad() >= seccion.getCapacidad();
    }

    private String hora(LocalTime t) {
        return t.format(HORA);
    }

    private String nombre(Seccion seccion) {
        return seccion.getGrado().getNombre() + " " + seccion.getNombre();
    }

    private Seccion seccion(Long id) {
        return seccionRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Sección", id));
    }

    private Horario buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Clase del horario", id));
    }
}
