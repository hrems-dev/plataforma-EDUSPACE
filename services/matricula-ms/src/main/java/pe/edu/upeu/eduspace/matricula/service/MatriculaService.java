package pe.edu.upeu.eduspace.matricula.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.upeu.eduspace.matricula.client.AlumnosClient;
import pe.edu.upeu.eduspace.matricula.client.AlumnosGateway;
import pe.edu.upeu.eduspace.matricula.client.PlanificacionClient;
import pe.edu.upeu.eduspace.matricula.client.PlanificacionGateway;
import pe.edu.upeu.eduspace.matricula.config.KafkaConfig;
import pe.edu.upeu.eduspace.matricula.config.MatriculaProperties;
import pe.edu.upeu.eduspace.matricula.dto.Dtos.*;
import pe.edu.upeu.eduspace.matricula.dto.PageResponse;
import pe.edu.upeu.eduspace.matricula.entity.Matricula;
import pe.edu.upeu.eduspace.matricula.entity.MatriculaHistorial;
import pe.edu.upeu.eduspace.matricula.entity.MatriculaRequisito;
import pe.edu.upeu.eduspace.matricula.event.MatriculaEvent;
import pe.edu.upeu.eduspace.matricula.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.matricula.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.matricula.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class MatriculaService {

    static final List<String> REQUISITOS = List.of("FICHA_FIRMADA", "CONSTANCIA_NO_ADEUDO", "CERTIFICADO_ESTUDIOS", "EXPEDIENTE_COMPLETO");

    private final MatriculaRepository matriculaRepository;
    private final CampaniaRepository campaniaRepository;
    private final RequisitoRepository requisitoRepository;
    private final HistorialRepository historialRepository;
    private final CupoService cupoService;
    private final EventoOutboxService outbox;
    private final AlumnosGateway alumnos;
    private final PlanificacionGateway planificacion;
    private final MatriculaProperties props;
    private final TransactionTemplate tx;

    public MatriculaService(MatriculaRepository matriculaRepository, CampaniaRepository campaniaRepository,
                            RequisitoRepository requisitoRepository, HistorialRepository historialRepository,
                            CupoService cupoService, EventoOutboxService outbox, AlumnosGateway alumnos,
                            PlanificacionGateway planificacion, MatriculaProperties props,
                            PlatformTransactionManager txManager) {
        this.matriculaRepository = matriculaRepository;
        this.campaniaRepository = campaniaRepository;
        this.requisitoRepository = requisitoRepository;
        this.historialRepository = historialRepository;
        this.cupoService = cupoService;
        this.outbox = outbox;
        this.alumnos = alumnos;
        this.planificacion = planificacion;
        this.props = props;
        this.tx = new TransactionTemplate(txManager);
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public PageResponse<MatriculaResponse> listar(Long anioId, Long gradoId, Long seccionId, String estado, String texto, Pageable pageable) {
        Page<Matricula> page = matriculaRepository.buscar(anioId, gradoId, seccionId, vacioANull(estado), texto == null ? "" : texto.trim(), pageable);
        return PageResponse.from(page.map(MatriculaMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public MatriculaDetalleResponse detalle(Long id) {
        Matricula m = obtener(id);
        return new MatriculaDetalleResponse(MatriculaMapper.toResponse(m),
            requisitoRepository.findByMatriculaIdOrderByTipo(id).stream().map(MatriculaMapper::toResponse).toList(),
            historialRepository.findByMatriculaIdOrderByFechaDescIdDesc(id).stream().map(MatriculaMapper::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public Matricula obtener(Long id) {
        return matriculaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Matrícula no encontrada: " + id));
    }

    @Transactional(readOnly = true)
    public List<MatriculaResponse> delosAlumnos(Set<Long> alumnoIds) {
        if (alumnoIds.isEmpty()) return List.of();
        return matriculaRepository.findByAlumnoIdIn(alumnoIds).stream().map(MatriculaMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AlumnoSeccionResponse> alumnosDeSeccion(Long seccionId) {
        return matriculaRepository.findBySeccionIdAndEstadoIn(seccionId, List.of("PRE_MATRICULA", "POR_APROBAR", "MATRICULADO")).stream()
            .map(m -> new AlumnoSeccionResponse(m.getId(), m.getCodigo(), m.getAlumnoId(), m.getAlumnoNombre(), m.getEstado()))
            .sorted(java.util.Comparator.comparing(AlumnoSeccionResponse::alumnoNombre))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<InternalMatricula> vivasDeSeccion(Long seccionId) {
        return matriculaRepository.findBySeccionIdAndEstadoIn(seccionId, List.of("PRE_MATRICULA", "POR_APROBAR", "MATRICULADO")).stream()
            .map(this::internal).toList();
    }

    @Transactional(readOnly = true)
    public InternalMatricula vigenteDeAlumno(Long alumnoId, Long anioId) {
        Long anio = anioId != null ? anioId : planificacion.anioActivo().id();
        return matriculaRepository.findVivaByAlumnoAndAnio(alumnoId, anio)
            .map(this::internal)
            .orElseThrow(() -> new ResourceNotFoundException("El alumno " + alumnoId + " no tiene matrícula vigente en el año " + anio));
    }

    private InternalMatricula internal(Matricula m) {
        return new InternalMatricula(m.getId(), m.getAlumnoId(), m.getAlumnoNombre(), m.getAnioId(), m.getNivelId(),
            m.getGradoId(), m.getSeccionId(), m.getEstado(), m.getTipo());
    }

    // ------------------------------------------------------------------ creación

    /** Validaciones externas fuera de la transacción; cupo + matrícula + historial + outbox en UNA transacción. */
    public MatriculaResponse crear(MatriculaCreateRequest req, Long usuarioId) {
        return crearInterno(req.alumnoId(), req.seccionId(), req.tipo() != null ? req.tipo() : "NUEVA",
            req.observaciones(), null, usuarioId);
    }

    MatriculaResponse crearInterno(Long alumnoId, Long seccionId, String tipo, String observaciones,
                                   Long matriculaAnteriorId, Long usuarioId) {
        return crearInterno(alumnoId, seccionId, tipo, observaciones, matriculaAnteriorId, usuarioId, true, props.getDiasVigenciaPrematricula());
    }

    /**
     * Pre-matrícula de la matrícula en línea: el expediente (DNI, partida y ficha SIAGIE) se verifica después en secretaría,
     * y la reserva vence pronto (el cupo no queda retenido si no se paga).
     */
    public MatriculaResponse crearEnLinea(Long alumnoId, Long seccionId, int diasVigencia) {
        return crearInterno(alumnoId, seccionId, "NUEVA", "Matrícula en línea (sitio web)", null, null, false, diasVigencia);
    }

    private MatriculaResponse crearInterno(Long alumnoId, Long seccionId, String tipo, String observaciones,
                                           Long matriculaAnteriorId, Long usuarioId, boolean exigirExpediente, int diasVigencia) {
        PlanificacionClient.Seccion sec = planificacion.seccion(seccionId);
        var campania = campaniaRepository.findFirstByAnioIdAndEstado(sec.anioId(), "ABIERTA")
            .orElseThrow(() -> new BusinessRuleException("No hay una campaña de matrícula ABIERTA para el año de la sección."));

        AlumnosClient.AlumnoBasico alumno = alumnos.alumno(alumnoId);
        if (!"ACTIVO".equalsIgnoreCase(alumno.estado())) {
            throw new BusinessRuleException("El alumno no está ACTIVO (estado actual: " + alumno.estado() + ").");
        }
        if (matriculaRepository.findVivaByAlumnoAndAnio(alumnoId, sec.anioId()).isPresent()) {
            throw new BusinessRuleException("El alumno ya tiene una matrícula vigente en este año.");
        }
        if (exigirExpediente) {
            AlumnosClient.Expediente exp = alumnos.expediente(alumnoId);
            if (!exp.completo()) {
                throw new BusinessRuleException("El expediente del alumno está incompleto. Faltan: "
                    + (exp.faltantes() == null ? "documentos" : String.join(", ", exp.faltantes())));
            }
        }

        PlanificacionClient.Anio anio = planificacion.anio(sec.anioId());
        AlumnosClient.ResponsablePago responsable = alumnos.responsablePagoOpcional(alumnoId);
        PlanificacionClient.Grado grado = planificacion.grado(sec.gradoId());

        return tx.execute(status -> {
            cupoService.asegurar(sec);
            cupoService.reservar(sec.id());

            LocalDateTime ahora = LocalDateTime.now();
            String codigo = String.format("MAT-%s-%06d", anio.nombre() != null ? anio.nombre() : sec.anioId(),
                matriculaRepository.siguienteCorrelativo());
            Matricula m = matriculaRepository.save(Matricula.builder()
                .codigo(codigo)
                .alumnoId(alumnoId)
                .alumnoNombre(alumno.nombreCompleto())
                .anioId(sec.anioId())
                .nivelId(sec.nivelId())
                .gradoId(sec.gradoId())
                .seccionId(sec.id())
                .campaniaId(campania.getId())
                .tipo(tipo)
                .estado("PRE_MATRICULA")
                .matriculaAnteriorId(matriculaAnteriorId)
                .venceEn(ahora.plusDays(diasVigencia))
                .observaciones(observaciones)
                .creadoPor(usuarioId)
                .creadoEn(ahora)
                .actualizadoEn(ahora)
                .build());

            for (String tipoReq : REQUISITOS) {
                boolean cumplido = "EXPEDIENTE_COMPLETO".equals(tipoReq) && exigirExpediente;
                requisitoRepository.save(MatriculaRequisito.builder()
                    .matriculaId(m.getId()).tipo(tipoReq).cumplido(cumplido)
                    .verificadoPor(cumplido ? usuarioId : null).verificadoEn(cumplido ? ahora : null)
                    .observacion(cumplido ? "Verificado automáticamente contra alumnos-ms" : null)
                    .build());
            }
            historial(m.getId(), "CREACION", null, "PRE_MATRICULA", null, sec.id(), observaciones, usuarioId);

            MatriculaEvent ev = evento(m, anio.nombre(), grado.nombre(), sec.nombre(), null, null);
            if (responsable != null) {
                ev.setResponsablePagoUsuarioId(responsable.usuarioId());
                ev.setResponsablePagoNombre(responsable.nombre());
            }
            outbox.registrar(KafkaConfig.TOPIC_MATRICULA_CREADA, ev);
            log.info("Matrícula {} creada para alumno {} en sección {}", codigo, alumnoId, sec.id());
            return MatriculaMapper.toResponse(m);
        });
    }

    // ------------------------------------------------------------------ confirmación (por pago)

    /**
     * Registra el pago de la matrícula: la pre-matrícula pasa a POR_APROBAR (ya no vence y sigue ocupando el cupo), pero todavía NO
     * es una matrícula: no se publica {@code matricula.confirmada} ni se generan pensiones hasta que la administración la apruebe.
     * Idempotente: si ya estaba POR_APROBAR o MATRICULADO no hace nada.
     */
    @Transactional
    public void confirmarPorPago(Long matriculaId, Long pagoId) {
        Matricula m = matriculaRepository.findById(matriculaId)
            .orElseThrow(() -> new ResourceNotFoundException("Matrícula no encontrada: " + matriculaId));
        if ("MATRICULADO".equals(m.getEstado()) || "POR_APROBAR".equals(m.getEstado())) {
            log.info("Matrícula {} ya tenía el pago registrado ({}); pago {} ignorado.", matriculaId, m.getEstado(), pagoId);
            return;
        }
        if (!"PRE_MATRICULA".equals(m.getEstado())) {
            log.warn("ALERTA: pago {} recibido para matrícula {} en estado {}; requiere decisión manual.", pagoId, matriculaId, m.getEstado());
            historial(m.getId(), "CONFIRMACION", m.getEstado(), m.getEstado(), null, null,
                "Pago " + pagoId + " recibido con la matrícula en estado " + m.getEstado() + "; no se aprobó.", null);
            return;
        }
        String anterior = m.getEstado();
        m.setEstado("POR_APROBAR");
        m.setPagadaEn(LocalDateTime.now());
        m.setVenceEn(null);
        m.setActualizadoEn(LocalDateTime.now());
        matriculaRepository.save(m);
        historial(m.getId(), "PAGO", anterior, "POR_APROBAR", null, null, "Pago de matrícula " + pagoId + "; pendiente de aprobación", null);
    }

    // ------------------------------------------------------------------ aprobación administrativa

    /**
     * Aprueba una matrícula pagada: pasa a MATRICULADO y publica {@code matricula.confirmada} (pensiones, asistencia, tareas, etc.).
     * Idempotente: si ya estaba MATRICULADO devuelve el estado actual sin publicar otra vez.
     */
    @Transactional
    public MatriculaResponse aprobar(Long id, Long usuarioId) {
        Matricula m = obtener(id);
        if ("MATRICULADO".equals(m.getEstado())) return MatriculaMapper.toResponse(m);
        if ("PRE_MATRICULA".equals(m.getEstado())) {
            throw new BusinessRuleException("La matrícula aún no tiene el pago confirmado; no se puede aprobar.");
        }
        if (!"POR_APROBAR".equals(m.getEstado())) {
            throw new BusinessRuleException("Solo se puede aprobar una matrícula POR_APROBAR (estado actual: " + m.getEstado() + ").");
        }
        LocalDateTime ahora = LocalDateTime.now();
        m.setEstado("MATRICULADO");
        m.setConfirmadaEn(ahora);
        m.setAprobadaEn(ahora);
        m.setAprobadaPor(usuarioId);
        m.setVenceEn(null);
        m.setActualizadoEn(ahora);
        matriculaRepository.save(m);
        historial(m.getId(), "APROBACION", "POR_APROBAR", "MATRICULADO", null, null, "Aprobada por la administración", usuarioId);
        outbox.registrar(KafkaConfig.TOPIC_MATRICULA_CONFIRMADA, eventoConNombres(m, null, null));
        log.info("Matrícula {} aprobada por el usuario {}", m.getCodigo(), usuarioId);
        return MatriculaMapper.toResponse(m);
    }

    /** Rechaza una matrícula pagada: se anula, se libera el cupo y se publica {@code matricula.anulada}. El pago se devuelve desde Finanzas. */
    @Transactional
    public MatriculaResponse rechazar(Long id, String motivo, Long usuarioId) {
        Matricula m = obtener(id);
        if (!"POR_APROBAR".equals(m.getEstado())) {
            throw new BusinessRuleException("Solo se puede rechazar una matrícula POR_APROBAR (estado actual: " + m.getEstado() + ").");
        }
        if (motivo == null || motivo.isBlank()) throw new BusinessRuleException("Indica el motivo del rechazo.");
        return cerrar(m, "ANULADO", "RECHAZO", KafkaConfig.TOPIC_MATRICULA_ANULADA, motivo.trim(), usuarioId);
    }

    // ------------------------------------------------------------------ traslado

    public MatriculaResponse trasladar(Long id, TrasladoRequest req, Long usuarioId) {
        Matricula previa = obtener(id);
        PlanificacionClient.Seccion nueva = planificacion.seccion(req.nuevaSeccionId());
        if (nueva.id().equals(previa.getSeccionId())) {
            throw new BusinessRuleException("La matrícula ya pertenece a esa sección.");
        }
        if (!nueva.gradoId().equals(previa.getGradoId()) || !nueva.anioId().equals(previa.getAnioId())) {
            throw new BusinessRuleException("El traslado de sección debe ser dentro del mismo grado y año.");
        }
        return tx.execute(status -> {
            // Primero el cupo (consultas atómicas); si no hay cupo se lanza antes de tocar nada
            cupoService.asegurar(nueva);
            cupoService.reservar(nueva.id());
            Matricula m = obtener(id);
            if (!Set.of("PRE_MATRICULA", "MATRICULADO").contains(m.getEstado())) {
                throw new BusinessRuleException("Solo se puede trasladar de sección una matrícula vigente (estado " + m.getEstado() + ").");
            }
            Long anterior = m.getSeccionId();
            cupoService.liberar(anterior);
            m.setSeccionId(nueva.id());
            m.setActualizadoEn(LocalDateTime.now());
            matriculaRepository.save(m);
            historial(m.getId(), "TRASLADO_SECCION", m.getEstado(), m.getEstado(), anterior, nueva.id(), req.motivo(), usuarioId);
            outbox.registrar(KafkaConfig.TOPIC_MATRICULA_TRASLADADA, eventoConNombres(m, req.motivo(), anterior));
            return MatriculaMapper.toResponse(m);
        });
    }

    // ------------------------------------------------------------------ retiro / anulación

    @Transactional
    public MatriculaResponse retirar(Long id, RetiroRequest req, Long usuarioId) {
        Matricula m = obtener(id);
        if (!"MATRICULADO".equals(m.getEstado())) {
            throw new BusinessRuleException("Solo se puede retirar una matrícula MATRICULADO (estado actual: " + m.getEstado() + ").");
        }
        return cerrar(m, "RETIRADO", "RETIRO", KafkaConfig.TOPIC_MATRICULA_RETIRADA, req.motivo(), usuarioId);
    }

    @Transactional
    public MatriculaResponse anular(Long id, String motivo, Long usuarioId) {
        Matricula m = obtener(id);
        if (!"PRE_MATRICULA".equals(m.getEstado())) {
            throw new BusinessRuleException("Solo se puede anular una PRE_MATRICULA (estado actual: " + m.getEstado() + ").");
        }
        return cerrar(m, "ANULADO", "ANULACION", KafkaConfig.TOPIC_MATRICULA_ANULADA, motivo, usuarioId);
    }

    /** Usado por el job de vencimiento: anula una PRE_MATRICULA vencida liberando su cupo. */
    @Transactional
    public void anularPorVencimiento(Long id) {
        Matricula m = obtener(id);
        if ("PRE_MATRICULA".equals(m.getEstado()) && m.getVenceEn() != null && m.getVenceEn().isBefore(LocalDateTime.now())) {
            cerrar(m, "ANULADO", "ANULACION", KafkaConfig.TOPIC_MATRICULA_ANULADA, "Pre-matrícula vencida sin pago", null);
        }
    }

    private MatriculaResponse cerrar(Matricula m, String estadoNuevo, String tipoCambio, String topic, String motivo, Long usuarioId) {
        String anterior = m.getEstado();
        cupoService.liberar(m.getSeccionId());
        Matricula ref = obtener(m.getId());
        ref.setEstado(estadoNuevo);
        ref.setCerradaEn(LocalDateTime.now());
        ref.setMotivoCierre(motivo);
        ref.setVenceEn(null);
        ref.setActualizadoEn(LocalDateTime.now());
        matriculaRepository.save(ref);
        historial(ref.getId(), tipoCambio, anterior, estadoNuevo, null, null, motivo, usuarioId);
        outbox.registrar(topic, eventoConNombres(ref, motivo, null));
        log.info("Matrícula {} -> {} ({})", ref.getId(), estadoNuevo, motivo);
        return MatriculaMapper.toResponse(ref);
    }

    // ------------------------------------------------------------------ utilidades

    void historial(Long matriculaId, String tipo, String anterior, String nuevo, Long secAnterior, Long secNueva,
                   String motivo, Long usuarioId) {
        historialRepository.save(MatriculaHistorial.builder()
            .matriculaId(matriculaId).tipoCambio(tipo).estadoAnterior(anterior).estadoNuevo(nuevo)
            .seccionAnteriorId(secAnterior).seccionNuevaId(secNueva).motivo(motivo).usuarioId(usuarioId).build());
    }

    private MatriculaEvent evento(Matricula m, String anio, String gradoNombre, String seccionNombre, String motivo, Long seccionAnterior) {
        return MatriculaEvent.builder()
            .matriculaId(m.getId()).alumnoId(m.getAlumnoId()).alumnoNombre(m.getAlumnoNombre())
            .anio(anio).anioId(m.getAnioId()).nivelId(m.getNivelId())
            .gradoId(m.getGradoId()).gradoNombre(gradoNombre)
            .seccionId(m.getSeccionId()).seccionNombre(seccionNombre)
            .estado(m.getEstado()).tipo(m.getTipo()).motivo(motivo).seccionAnteriorId(seccionAnterior)
            .build();
    }

    /** Arma el evento con nombres de año/grado/sección; si planificación no responde, van sin nombres (solo informativos). */
    private MatriculaEvent eventoConNombres(Matricula m, String motivo, Long seccionAnterior) {
        String anio = null, grado = null, seccion = null;
        try { anio = planificacion.anio(m.getAnioId()).nombre(); } catch (Exception e) { log.debug("Sin nombre de año: {}", e.getMessage()); }
        try { grado = planificacion.grado(m.getGradoId()).nombre(); } catch (Exception e) { log.debug("Sin nombre de grado: {}", e.getMessage()); }
        try { seccion = planificacion.seccion(m.getSeccionId()).nombre(); } catch (Exception e) { log.debug("Sin nombre de sección: {}", e.getMessage()); }
        MatriculaEvent ev = evento(m, anio, grado, seccion, motivo, seccionAnterior);
        AlumnosClient.ResponsablePago rp = alumnos.responsablePagoOpcional(m.getAlumnoId());
        if (rp != null) {
            ev.setResponsablePagoUsuarioId(rp.usuarioId());
            ev.setResponsablePagoNombre(rp.nombre());
        }
        return ev;
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
