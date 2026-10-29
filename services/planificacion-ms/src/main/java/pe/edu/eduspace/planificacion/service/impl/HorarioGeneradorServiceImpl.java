package pe.edu.eduspace.planificacion.service.impl;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import pe.edu.eduspace.planificacion.dto.GenerarHorarioRequest;
import pe.edu.eduspace.planificacion.dto.PreparacionSeccionResponse;
import pe.edu.eduspace.planificacion.dto.ResultadoGeneracion;
import pe.edu.eduspace.planificacion.dto.ResultadoSeccionGeneracion;
import pe.edu.eduspace.planificacion.entity.*;
import pe.edu.eduspace.planificacion.exception.BusinessRuleException;
import pe.edu.eduspace.planificacion.exception.ResourceNotFoundException;
import pe.edu.eduspace.planificacion.horario.PlanificadorHorario;
import pe.edu.eduspace.planificacion.horario.PlanificadorHorario.Leccion;
import pe.edu.eduspace.planificacion.repository.*;
import pe.edu.eduspace.planificacion.service.HorarioGeneradorService;

@Service
@RequiredArgsConstructor
@Transactional
public class HorarioGeneradorServiceImpl implements HorarioGeneradorService {

    /** Lunes a viernes. */
    private static final int DIAS = 5;
    private static final long SEMILLA = 2026L;
    private static final Pattern GENERALISTA = Pattern.compile("primaria|polivalente|generalista|inicial|tutor");

    private final AnioAcademicoRepository anioRepository;
    private final SeccionRepository seccionRepository;
    private final GradoCursoRepository mallaRepository;
    private final AsignacionRepository asignacionRepository;
    private final BloqueHorarioRepository bloqueRepository;
    private final HorarioRepository horarioRepository;
    private final DocenteRepository docenteRepository;

    /** Datos del año leídos una sola vez. */
    private static final class Contexto {
        final Map<Long, List<GradoCurso>> malla = new HashMap<>();
        final Map<Turno, List<BloqueHorario>> bloques = new HashMap<>();
        final Map<Long, List<Asignacion>> asignaciones = new HashMap<>();
        final Map<Long, Integer> horasDocente = new HashMap<>();
        final Map<Long, Long> programadasPorAsignacion = new HashMap<>();
    }

    // ------------------------------------------------------------------ preparación
    @Override
    @Transactional(readOnly = true)
    public List<PreparacionSeccionResponse> preparacion(Long anioId) {
        anio(anioId);
        List<Seccion> secciones = seccionesActivas(anioId);
        Contexto ctx = cargar(secciones, anioId);
        return secciones.stream().map(s -> evaluar(s, ctx)).toList();
    }

    private PreparacionSeccionResponse evaluar(Seccion s, Contexto ctx) {
        List<GradoCurso> malla = ctx.malla.get(s.getGrado().getId());
        List<BloqueHorario> clase = bloquesDeClase(ctx, s.getTurno());
        int espacios = clase.size() * DIAS;
        int horas = malla.stream().mapToInt(m -> m.getHorasSemanales()).sum();
        List<Asignacion> asignadas = ctx.asignaciones.getOrDefault(s.getId(), List.of());
        Map<Long, Asignacion> porCurso = new HashMap<>();
        asignadas.forEach(a -> porCurso.put(a.getCurso().getId(), a));
        List<String> sinDocente = malla.stream().filter(m -> !porCurso.containsKey(m.getCurso().getId()))
                .map(m -> m.getCurso().getNombre()).toList();
        int programadas = asignadas.stream().mapToInt(a -> ctx.programadasPorAsignacion.getOrDefault(a.getId(), 0L).intValue()).sum();
        List<String> problemas = new ArrayList<>();
        if (malla.isEmpty()) {
            problemas.add("El grado no tiene cursos en la malla");
        }
        if (clase.isEmpty()) {
            problemas.add("El turno " + s.getTurno() + " no tiene bloques de clase");
        } else if (horas > espacios) {
            problemas.add("La malla suma " + horas + " horas y la semana solo tiene " + espacios + " espacios");
        }
        if (!sinDocente.isEmpty()) {
            problemas.add("Faltan docentes en: " + String.join(", ", sinDocente));
        }
        asignadas.stream().map(Asignacion::getDocente).distinct().forEach(d -> {
            int carga = ctx.horasDocente.getOrDefault(d.getId(), 0);
            if (!clase.isEmpty() && carga > espacios) {
                problemas.add("El docente " + d.nombreCompleto() + " tiene " + carga + " horas asignadas y solo hay " + espacios + " espacios");
            }
        });
        return new PreparacionSeccionResponse(s.getId(), nombre(s), s.getGrado().getId(), s.getGrado().getNombre(),
                s.getGrado().getNivel().getId(), s.getGrado().getNivel().getNombre(), s.getTurno(),
                s.getAula() == null ? null : s.getAula().getNombre(), malla.size(), horas, espacios, sinDocente, programadas,
                problemas.isEmpty(), problemas);
    }

    // ------------------------------------------------------------------ generación
    @Override
    public ResultadoGeneracion generar(GenerarHorarioRequest req) {
        AnioAcademico anio = anio(req.anioId());
        if (anio.getEstado() == EstadoPeriodo.CERRADO) {
            throw new BusinessRuleException("El año " + anio.getNombre() + " está CERRADO (solo lectura)");
        }
        boolean reemplazar = Boolean.TRUE.equals(req.reemplazar());
        boolean simular = Boolean.TRUE.equals(req.simular());
        List<Seccion> todas = seccionesActivas(req.anioId());
        Set<Long> pedidas = req.seccionIds() == null ? Set.of() : Set.copyOf(req.seccionIds());
        List<Seccion> elegidas = todas.stream().filter(s -> pedidas.isEmpty() || pedidas.contains(s.getId())).toList();
        if (elegidas.isEmpty()) {
            throw new BusinessRuleException("No hay secciones para generar el horario");
        }

        Contexto ctx = cargar(todas, req.anioId());
        int docentesAsignados = 0;
        if (Boolean.TRUE.equals(req.asignarDocentes())) {
            docentesAsignados = autoAsignar(elegidas, ctx);
            if (docentesAsignados > 0) {
                ctx = cargar(todas, req.anioId()); // recarga con las asignaciones nuevas
            }
        }

        List<ResultadoSeccionGeneracion> resultados = new ArrayList<>();
        List<Seccion> listas = new ArrayList<>();
        for (Seccion s : elegidas) {
            PreparacionSeccionResponse prep = evaluar(s, ctx);
            if (prep.lista()) {
                listas.add(s);
            } else {
                resultados.add(new ResultadoSeccionGeneracion(s.getId(), nombre(s), "OMITIDO", prep.horasMalla(), 0, prep.problemas()));
            }
        }

        int colocadas = 0;
        int sinColocar = 0;
        if (!listas.isEmpty()) {
            Resolucion r = resolver(listas, ctx, req.anioId(), reemplazar, simular);
            resultados.addAll(r.resultados);
            colocadas = r.colocadas;
            sinColocar = r.sinColocar;
        }
        if (simular) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly(); // nada de esto se guarda
        }
        int generadas = (int) resultados.stream().filter(x -> x.estado().equals("GENERADO") || x.estado().equals("INCOMPLETO")).count();
        int omitidas = (int) resultados.stream().filter(x -> x.estado().equals("OMITIDO")).count();
        resultados.sort(Comparator.comparing(ResultadoSeccionGeneracion::nombre));
        return new ResultadoGeneracion(simular, generadas, omitidas, colocadas, sinColocar, docentesAsignados, resultados);
    }

    private static final class Resolucion {
        final List<ResultadoSeccionGeneracion> resultados = new ArrayList<>();
        int colocadas;
        int sinColocar;
    }

    private Resolucion resolver(List<Seccion> listas, Contexto ctx, Long anioId, boolean reemplazar, boolean simular) {
        // bloques de clase de los turnos que intervienen: el espacio es (día, bloque) y un docente no puede repetirse entre turnos solapados
        List<BloqueHorario> bloques = new ArrayList<>();
        for (Turno t : listas.stream().map(Seccion::getTurno).distinct().sorted().toList()) {
            bloques.addAll(bloquesDeClase(ctx, t));
        }
        int nb = bloques.size();
        Map<Long, Integer> indiceBloque = new HashMap<>();
        for (int i = 0; i < nb; i++) indiceBloque.put(bloques.get(i).getId(), i);
        int e = DIAS * nb;

        Map<Long, Integer> idxSeccion = new HashMap<>();
        for (int i = 0; i < listas.size(); i++) idxSeccion.put(listas.get(i).getId(), i);
        Map<Long, Integer> idxDocente = new LinkedHashMap<>();
        Map<Long, Integer> idxAula = new LinkedHashMap<>();
        Map<Long, Integer> idxCurso = new LinkedHashMap<>();
        for (Seccion s : listas) {
            for (Asignacion a : ctx.asignaciones.getOrDefault(s.getId(), List.of())) {
                idxDocente.putIfAbsent(a.getDocente().getId(), idxDocente.size());
                idxCurso.putIfAbsent(a.getCurso().getId(), idxCurso.size());
            }
            if (s.getAula() != null) idxAula.putIfAbsent(s.getAula().getId(), idxAula.size());
        }

        boolean[][] permitido = new boolean[listas.size()][e];
        for (int i = 0; i < listas.size(); i++) {
            for (int d = 0; d < DIAS; d++) {
                for (int b = 0; b < nb; b++) permitido[i][d * nb + b] = bloques.get(b).getTurno() == listas.get(i).getTurno();
            }
        }
        boolean[][] ocupSeccion = new boolean[listas.size()][e];
        boolean[][] ocupDocente = new boolean[Math.max(idxDocente.size(), 1)][e];
        boolean[][] ocupAula = new boolean[Math.max(idxAula.size(), 1)][e];

        // clases que ya existen: ocupan espacios (las de las secciones a regenerar se borrarán, por eso no cuentan)
        Map<Long, Long> yaColocadas = new HashMap<>();
        List<Horario> aBorrar = new ArrayList<>();
        for (Horario h : horarioRepository.findByAnio(anioId)) {
            Long seccionId = h.getAsignacion().getSeccion().getId();
            boolean regenera = idxSeccion.containsKey(seccionId);
            if (regenera && reemplazar) {
                aBorrar.add(h);
                continue;
            }
            Integer ib = indiceBloque.get(h.getBloque().getId());
            if (ib == null || h.getDiaSemana() > DIAS) continue;
            int slot = (h.getDiaSemana() - 1) * nb + ib;
            if (regenera) {
                ocupSeccion[idxSeccion.get(seccionId)][slot] = true;
                yaColocadas.merge(h.getAsignacion().getId(), 1L, Long::sum);
            }
            Integer d = idxDocente.get(h.getAsignacion().getDocente().getId());
            if (d != null) ocupDocente[d][slot] = true;
            if (h.getAula() != null && idxAula.get(h.getAula().getId()) != null) ocupAula[idxAula.get(h.getAula().getId())][slot] = true;
        }

        // una lección por cada hora que falta de cada curso
        List<Leccion> lecciones = new ArrayList<>();
        List<Asignacion> deLeccion = new ArrayList<>();
        Map<Long, Integer> requeridasPorSeccion = new HashMap<>();
        for (Seccion s : listas) {
            Map<Long, Asignacion> porCurso = new HashMap<>();
            ctx.asignaciones.getOrDefault(s.getId(), List.of()).forEach(a -> porCurso.put(a.getCurso().getId(), a));
            int req = 0;
            for (GradoCurso m : ctx.malla.get(s.getGrado().getId())) {
                Asignacion a = porCurso.get(m.getCurso().getId());
                int faltan = m.getHorasSemanales() - yaColocadas.getOrDefault(a.getId(), 0L).intValue();
                req += m.getHorasSemanales();
                for (int h = 0; h < faltan; h++) {
                    lecciones.add(new Leccion(idxSeccion.get(s.getId()), idxDocente.get(a.getDocente().getId()),
                            idxCurso.get(m.getCurso().getId()), s.getAula() == null ? -1 : idxAula.get(s.getAula().getId())));
                    deLeccion.add(a);
                }
            }
            requeridasPorSeccion.put(s.getId(), req);
        }

        int[] sol = PlanificadorHorario.resolver(
                new PlanificadorHorario.Problema(DIAS, nb, lecciones, permitido, ocupSeccion, ocupDocente, ocupAula, idxCurso.size()), SEMILLA);

        if (reemplazar && !aBorrar.isEmpty()) {
            horarioRepository.deleteAll(aBorrar);
            horarioRepository.flush();
        }
        Resolucion out = new Resolucion();
        Map<Long, Integer> colocadasPorSeccion = new HashMap<>();
        Map<Long, Map<String, Integer>> faltanPorSeccion = new HashMap<>();
        List<Horario> nuevos = new ArrayList<>();
        for (int l = 0; l < lecciones.size(); l++) {
            Asignacion a = deLeccion.get(l);
            Long seccionId = a.getSeccion().getId();
            if (sol[l] < 0) {
                out.sinColocar++;
                faltanPorSeccion.computeIfAbsent(seccionId, k -> new LinkedHashMap<>()).merge(a.getCurso().getNombre(), 1, Integer::sum);
                continue;
            }
            Horario h = new Horario();
            h.setAsignacion(a);
            h.setBloque(bloques.get(sol[l] % nb));
            h.setDiaSemana((short) (sol[l] / nb + 1));
            h.setAula(a.getSeccion().getAula());
            nuevos.add(h);
            colocadasPorSeccion.merge(seccionId, 1, Integer::sum);
            out.colocadas++;
        }
        horarioRepository.saveAll(nuevos);

        for (Seccion s : listas) {
            int col = colocadasPorSeccion.getOrDefault(s.getId(), 0);
            int existentes = (int) existentesDe(s, ctx, yaColocadas);
            boolean teniaClases = existentes > 0 || aBorrar.stream().anyMatch(h -> h.getAsignacion().getSeccion().getId().equals(s.getId()));
            Map<String, Integer> faltan = faltanPorSeccion.getOrDefault(s.getId(), Map.of());
            List<String> msg = new ArrayList<>();
            faltan.forEach((curso, n) -> msg.add(curso + ": falta ubicar " + n + " hora(s)"));
            String estado;
            if (!faltan.isEmpty()) {
                estado = "INCOMPLETO";
                msg.add("Revisa si un docente está sobrecargado o si el turno tiene pocos bloques.");
            } else if (col == 0) {
                estado = "SIN_CAMBIOS";
                msg.add("El horario ya estaba completo.");
            } else {
                estado = "GENERADO";
                if (teniaClases && !simular) msg.add("Si el horario ya estaba publicado, vuelve a publicarlo para avisar a las familias.");
            }
            out.resultados.add(new ResultadoSeccionGeneracion(s.getId(), nombre(s), estado, requeridasPorSeccion.get(s.getId()),
                    col + existentes, msg));
        }
        return out;
    }

    private long existentesDe(Seccion s, Contexto ctx, Map<Long, Long> yaColocadas) {
        return ctx.asignaciones.getOrDefault(s.getId(), List.of()).stream().mapToLong(a -> yaColocadas.getOrDefault(a.getId(), 0L)).sum();
    }

    // ------------------------------------------------------------------ asignar docentes por especialidad
    private int autoAsignar(List<Seccion> elegidas, Contexto ctx) {
        List<Docente> activos = docenteRepository.findAll().stream().filter(d -> d.getEstado() == EstadoDocente.ACTIVO).toList();
        int creadas = 0;
        for (Seccion s : elegidas) {
            Set<Long> conDocente = new java.util.HashSet<>();
            ctx.asignaciones.getOrDefault(s.getId(), List.of()).forEach(a -> conDocente.add(a.getCurso().getId()));
            int espacios = bloquesDeClase(ctx, s.getTurno()).size() * DIAS;
            for (GradoCurso m : ctx.malla.get(s.getGrado().getId())) {
                if (conDocente.contains(m.getCurso().getId())) continue;
                Docente elegido = null;
                int mejorPrioridad = Integer.MAX_VALUE;
                int menorCarga = Integer.MAX_VALUE;
                for (Docente d : activos) {
                    int prioridad = prioridad(d, m.getCurso(), s.getGrado().getNivel());
                    if (prioridad > 1) continue;
                    int carga = ctx.horasDocente.getOrDefault(d.getId(), 0);
                    if (espacios > 0 && carga + m.getHorasSemanales() > espacios) continue; // no entra en su semana
                    if (prioridad < mejorPrioridad || (prioridad == mejorPrioridad && carga < menorCarga)) {
                        elegido = d;
                        mejorPrioridad = prioridad;
                        menorCarga = carga;
                    }
                }
                if (elegido == null) continue;
                Asignacion a = new Asignacion();
                a.setSeccion(s);
                a.setCurso(m.getCurso());
                a.setDocente(elegido);
                asignacionRepository.save(a);
                ctx.horasDocente.merge(elegido.getId(), (int) m.getHorasSemanales(), Integer::sum);
                creadas++;
            }
        }
        asignacionRepository.flush();
        return creadas;
    }

    /** 0 = su especialidad es el curso; 1 = docente generalista del nivel (Primaria/Inicial); 2 = no corresponde. */
    static int prioridad(Docente d, Curso curso, Nivel nivel) {
        String esp = normalizar(d.getEspecialidad());
        if (esp.isBlank()) return 2;
        String nombre = normalizar(curso.getNombre());
        String area = normalizar(curso.getArea());
        if (esp.contains(nombre) || (esp.length() >= 4 && nombre.contains(esp)) || (!area.isBlank() && area.length() >= 4 && esp.contains(area))) {
            return 0;
        }
        boolean nivelInicialOPrimaria = nivel != null && ("PRIMARIA".equals(nivel.getCodigo()) || "INICIAL".equals(nivel.getCodigo()));
        return nivelInicialOPrimaria && GENERALISTA.matcher(esp).find() ? 1 : 2;
    }

    static String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    // ------------------------------------------------------------------ carga de datos
    private Contexto cargar(List<Seccion> secciones, Long anioId) {
        Contexto ctx = new Contexto();
        for (Seccion s : secciones) {
            ctx.malla.computeIfAbsent(s.getGrado().getId(), g -> mallaRepository.findByGradoIdOrderByCursoNombre(g));
            ctx.bloques.computeIfAbsent(s.getTurno(), bloqueRepository::findByTurnoOrderByOrden);
            List<Asignacion> asignadas = asignacionRepository.findBySeccionId(s.getId());
            ctx.asignaciones.put(s.getId(), asignadas);
            for (Asignacion a : asignadas) {
                ctx.programadasPorAsignacion.computeIfAbsent(a.getId(), horarioRepository::countByAsignacionId);
                int horas = ctx.malla.get(s.getGrado().getId()).stream().filter(m -> m.getCurso().getId().equals(a.getCurso().getId()))
                        .mapToInt(m -> m.getHorasSemanales()).sum();
                ctx.horasDocente.merge(a.getDocente().getId(), horas, Integer::sum);
            }
        }
        return ctx;
    }

    private List<BloqueHorario> bloquesDeClase(Contexto ctx, Turno turno) {
        return ctx.bloques.computeIfAbsent(turno, bloqueRepository::findByTurnoOrderByOrden).stream()
                .filter(b -> !Boolean.TRUE.equals(b.getEsReceso())).toList();
    }

    private List<Seccion> seccionesActivas(Long anioId) {
        return seccionRepository.buscar(anioId, null).stream().filter(s -> Boolean.TRUE.equals(s.getActivo())).toList();
    }

    private AnioAcademico anio(Long id) {
        return anioRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Año académico", id));
    }

    private String nombre(Seccion s) {
        return s.getGrado().getNombre() + " " + s.getNombre();
    }
}
