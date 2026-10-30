package pe.edu.upeu.eduspace.matricula.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.upeu.eduspace.matricula.client.*;
import pe.edu.upeu.eduspace.matricula.config.MatriculaProperties;
import pe.edu.upeu.eduspace.matricula.dto.Dtos.CupoResponse;
import pe.edu.upeu.eduspace.matricula.dto.PublicoDtos.*;
import pe.edu.upeu.eduspace.matricula.entity.Matricula;
import pe.edu.upeu.eduspace.matricula.entity.MatriculaPublica;
import pe.edu.upeu.eduspace.matricula.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.matricula.exception.ServicioNoDisponibleException;
import pe.edu.upeu.eduspace.matricula.repository.CampaniaRepository;
import pe.edu.upeu.eduspace.matricula.repository.MatriculaPublicaRepository;
import pe.edu.upeu.eduspace.matricula.repository.MatriculaRepository;
import pe.edu.upeu.eduspace.matricula.util.PdfConstancia;
import pe.edu.upeu.eduspace.matricula.util.TarjetaSimulada;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Matrícula en línea desde el sitio público, de punta a punta: registra al alumno y a su apoderado, reserva el cupo
 * (pre-matrícula) y cobra la matrícula con la pasarela simulada; la matrícula queda POR_APROBAR. Cuando la administración la aprueba
 * se crean las cuentas de acceso (usuario nombre.apellido, clave inicial = documento) y se envía por correo la aprobación con los datos
 * para iniciar sesión, la constancia en PDF, el horario y las finanzas. Si la rechaza, se avisa por correo con el motivo.
 * Quien inicia el trámite recibe un token que debe presentar para pagar, ver el resumen o bajar la constancia.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatriculaEnLineaService {

    private static final String[] DIAS = {"", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"};
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MatriculaService matriculaService;
    private final MatriculaRepository matriculas;
    private final MatriculaPublicaRepository publicas;
    private final CampaniaRepository campanias;
    private final CupoService cupos;
    private final PlanificacionGateway planificacion;
    private final PlanificacionClient planificacionClient;
    private final AlumnosClient alumnos;
    private final PagoClient pago;
    private final AuthClient auth;
    private final NotificacionesClient notificaciones;
    private final MatriculaProperties props;

    // ------------------------------------------------------------------ oferta

    public Oferta oferta() {
        PlanificacionClient.Anio anio = planificacion.anioActivo();
        boolean abierta = campanias.findFirstByAnioIdAndEstado(anio.id(), "ABIERTA").isPresent();
        if (!abierta) {
            return new Oferta(anio.id(), anio.nombre(), false, "Las matrículas en línea no están abiertas por ahora. Escríbenos desde Contacto.", List.of());
        }
        Map<Long, Integer> vacantesPorGrado = new HashMap<>();
        for (CupoResponse c : cupos.vacantes(anio.id(), null)) vacantesPorGrado.merge(c.gradoId(), c.vacantes(), Integer::sum);
        Map<Long, List<GradoOferta>> porNivel = new TreeMap<>();
        Map<Long, String> nombreNivel = new HashMap<>();
        for (Long gradoId : new TreeSet<>(vacantesPorGrado.keySet())) {
            PlanificacionClient.Grado g = planificacion.grado(gradoId);
            PagoClient.OfertaCosto costo = costo(anio.id(), g.nivelId(), gradoId);
            PensionOferta pension = null;
            if (costo != null && costo.pension() != null) {
                int cuotas = costo.pension().numCuotas() == null ? 10 : costo.pension().numCuotas();
                pension = new PensionOferta(costo.pension().monto(), cuotas, costo.pension().monto().divide(BigDecimal.valueOf(cuotas), 2, RoundingMode.HALF_UP));
            }
            porNivel.computeIfAbsent(g.nivelId(), k -> new ArrayList<>()).add(new GradoOferta(g.id(), g.nombre(), vacantesPorGrado.get(gradoId),
                costo != null && costo.matricula() != null ? costo.matricula().monto() : null, pension));
            nombreNivel.put(g.nivelId(), g.nivelNombre());
        }
        List<NivelOferta> niveles = porNivel.entrySet().stream().map(e -> new NivelOferta(e.getKey(), nombreNivel.get(e.getKey()), e.getValue())).toList();
        return new Oferta(anio.id(), anio.nombre(), true, null, niveles);
    }

    // ------------------------------------------------------------------ iniciar

    public Inicio iniciar(Solicitud s) {
        PlanificacionClient.Anio anio = planificacion.anioActivo();
        if (campanias.findFirstByAnioIdAndEstado(anio.id(), "ABIERTA").isEmpty()) {
            throw new BusinessRuleException("Las matrículas en línea no están abiertas por ahora.");
        }
        PlanificacionClient.Grado grado = planificacion.grado(s.gradoId());
        List<CupoResponse> disponibles = cupos.vacantes(anio.id(), grado.id()).stream().filter(c -> c.vacantes() > 0)
            .sorted(Comparator.comparingInt(CupoResponse::vacantes).reversed().thenComparing(CupoResponse::seccionNombre)).toList();
        if (disponibles.isEmpty()) throw new BusinessRuleException("No quedan vacantes en " + grado.nombre() + ".");
        PagoClient.OfertaCosto costo = costo(anio.id(), grado.nivelId(), grado.id());
        if (costo == null || costo.matricula() == null) {
            throw new BusinessRuleException(grado.nombre() + " todavía no tiene tarifa de matrícula. Comunícate con secretaría.");
        }
        CupoResponse cupo = disponibles.get(0);          // la sección con más vacantes: reparte a los alumnos entre secciones

        AlumnoSolicitud a = s.alumno();
        ApoderadoSolicitud ap = s.apoderado();
        Long alumnoId;
        String alumnoNombre;
        Long apoderadoId;
        Long apoderadoUsuarioId;
        MatriculaPublica previa = retomable(a.numeroDocumento().trim(), ap.numeroDocumento().trim());
        if (previa != null) {
            // Una reserva anterior de esta misma familia venció sin pago: se reutiliza el registro del alumno en vez de pedir otro.
            alumnoId = matriculas.findById(previa.getMatriculaId()).orElseThrow().getAlumnoId();
            alumnoNombre = previa.getAlumnoApellidos() + ", " + previa.getAlumnoNombres();
            apoderadoId = previa.getApoderadoId();
            apoderadoUsuarioId = previa.getApoderadoUsuarioId();
        } else {
            AlumnosClient.AlumnoRegistrado reg = registrar(a, ap);
            alumnoId = reg.id();
            alumnoNombre = reg.nombreCompleto();
            apoderadoId = reg.apoderados().get(0).apoderado().id();
            apoderadoUsuarioId = reg.apoderados().get(0).apoderado().usuarioId();
        }

        var m = matriculaService.crearEnLinea(alumnoId, cupo.seccionId(), props.getDiasReservaEnLinea());
        PagoClient.CuotaResumen cuota = esperarCuotaMatricula(m.id());
        if (cuota == null) {
            matriculaService.anular(m.id(), "Matrícula en línea sin cuota de matrícula", null);
            throw new ServicioNoDisponibleException("No pudimos generar la cuota de matrícula. Intenta de nuevo en unos minutos.");
        }

        String token = generarToken();
        publicas.save(MatriculaPublica.builder()
            .matriculaId(m.id()).tokenHash(hash(token)).apoderadoId(apoderadoId).apoderadoUsuarioId(apoderadoUsuarioId)
            .apoderadoNombre(ap.nombres().trim() + " " + ap.apellidos().trim()).apoderadoNombres(ap.nombres().trim()).apoderadoApellidos(ap.apellidos().trim()).apoderadoDocumento(ap.numeroDocumento().trim())
            .apoderadoEmail(vacioANulo(ap.email())).alumnoNombres(a.nombres().trim())
            .alumnoApellidos((a.apellidoPaterno().trim() + " " + (a.apellidoMaterno() == null ? "" : a.apellidoMaterno().trim())).trim())
            .alumnoDocumento(a.numeroDocumento().trim()).montoMatricula(cuota.saldo()).build());
        log.info("Matrícula en línea iniciada: {} (sección {})", m.codigo(), cupo.seccionNombre());
        return new Inicio(m.codigo(), token, m.codigo(), alumnoNombre, grado.nombre(), cupo.seccionNombre(), turno(cupo.seccionId()),
            cuota.saldo(), m.venceEn() == null ? null : m.venceEn().format(FECHA_HORA));
    }

    private AlumnosClient.AlumnoRegistrado registrar(AlumnoSolicitud a, ApoderadoSolicitud ap) {
        try {
            return alumnos.registrar(new AlumnosClient.RegistroAlumno(def(a.tipoDocumento()), a.numeroDocumento().trim(), a.nombres().trim(),
                a.apellidoPaterno().trim(), vacioANulo(a.apellidoMaterno()), a.fechaNacimiento().toString(), a.sexo(), "Peruana", ap.telefono().trim(),
                null, List.of(new AlumnosClient.VinculoApoderado(new AlumnosClient.ApoderadoDatos(def(ap.tipoDocumento()), ap.numeroDocumento().trim(),
                    ap.nombres().trim(), ap.apellidos().trim(), ap.telefono().trim(), vacioANulo(ap.email())), ap.parentesco(), true, true, true, true))));
        } catch (FeignException.Conflict e) {
            throw new BusinessRuleException("Ya hay un alumno registrado con ese documento. Si ya tiene matrícula, ingresa al portal; si no, acércate a secretaría.");
        } catch (FeignException.BadRequest e) {
            throw new BusinessRuleException("Revisa los datos del alumno y del apoderado (documento, fecha de nacimiento y teléfono).");
        } catch (FeignException e) {
            throw new ServicioNoDisponibleException("No pudimos registrar la solicitud en este momento. Intenta de nuevo en unos minutos.");
        }
    }

    /** Intento anterior sin pago, ya vencido, del mismo alumno y con el mismo apoderado (mismo documento). */
    private MatriculaPublica retomable(String alumnoDocumento, String apoderadoDocumento) {
        return publicas.findFirstByAlumnoDocumentoAndEstadoOrderByCreadoEnDesc(alumnoDocumento, "INICIADA")
            .filter(p -> apoderadoDocumento.equalsIgnoreCase(p.getApoderadoDocumento()))
            .filter(p -> matriculas.findById(p.getMatriculaId()).map(x -> "ANULADO".equals(x.getEstado())).orElse(false))
            .orElse(null);
    }

    // ------------------------------------------------------------------ pagar (pasarela simulada)

    public Resumen pagar(String referencia, String token, Tarjeta tarjeta) {
        MatriculaPublica mp = autorizar(referencia, token);
        Matricula m = matriculas.findById(mp.getMatriculaId()).orElseThrow();
        if (!"INICIADA".equals(mp.getEstado())) return resumen(mp, m);
        if (!"PRE_MATRICULA".equals(m.getEstado())) {
            throw new BusinessRuleException("La reserva de esta matrícula ya no está vigente (estado " + m.getEstado() + "). Inicia una nueva solicitud.");
        }
        var r = TarjetaSimulada.procesar(tarjeta.numero(), tarjeta.vencimiento(), tarjeta.cvv(), YearMonth.now());
        if (!r.aprobada()) throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, r.mensaje());

        PagoClient.ResultadoCobro cobro;
        try {
            cobro = pago.cobrar(new PagoClient.CobroMatricula(m.getId(), m.getAlumnoId(), mp.getApoderadoNombre(), mp.getApoderadoDocumento(), r.referencia()));
        } catch (FeignException e) {
            log.warn("pago-ms rechazó el cobro de la matrícula {}: {}", m.getCodigo(), e.status());
            throw new ServicioNoDisponibleException("No pudimos registrar el pago. No se hizo ningún cobro; intenta de nuevo.");
        }
        matriculaService.confirmarPorPago(m.getId(), cobro.pagoId());   // → POR_APROBAR; idempotente: el evento pago.confirmado llegará después
        mp.setEstado("PAGADA");
        mp.setPagoId(cobro.pagoId());
        mp.setComprobante(cobro.comprobante());
        mp.setMontoMatricula(cobro.monto());
        mp.setPagadaEn(LocalDateTime.now());
        publicas.save(mp);

        Matricula enRevision = matriculas.findById(m.getId()).orElseThrow();
        Resumen res = resumen(mp, enRevision);
        enviarCorreoRecibido(mp, enRevision, res);
        publicas.save(mp);
        return resumen(mp, enRevision);
    }

    // ------------------------------------------------------------------ aprobación / rechazo (las dispara la administración)

    /**
     * Después de aprobar la matrícula: crea las cuentas de acceso y envía al apoderado el correo de aprobación con el usuario y la
     * clave inicial, la constancia, el horario y las finanzas. Solo aplica a trámites del sitio público; los demás los atiende secretaría.
     * Los pasos de cuentas y correo no deshacen la aprobación: si fallan quedan registrados y se pueden reenviar.
     */
    public void aprobada(Long matriculaId) {
        MatriculaPublica mp = publicas.findById(matriculaId).orElse(null);
        if (mp == null || "APROBADA".equals(mp.getEstado()) || "RECHAZADA".equals(mp.getEstado())) return;
        accesoYCorreo(mp, matriculas.findById(matriculaId).orElseThrow());
        mp.setEstado("APROBADA");
        publicas.save(mp);
    }

    /** Reenvía el correo de acceso de una matrícula ya aprobada (por ejemplo si el apoderado lo perdió o falló el envío). */
    public Resumen reenviarAcceso(Long matriculaId) {
        MatriculaPublica mp = publicas.findById(matriculaId)
            .orElseThrow(() -> new BusinessRuleException("Esta matrícula no se hizo en línea: el acceso lo entrega secretaría."));
        Matricula m = matriculas.findById(matriculaId).orElseThrow();
        if (!"MATRICULADO".equals(m.getEstado())) throw new BusinessRuleException("Solo se reenvía el acceso de una matrícula aprobada.");
        accesoYCorreo(mp, m);
        mp.setEstado("APROBADA");
        publicas.save(mp);
        return resumen(mp, m);
    }

    /** Después de rechazar: avisa por correo al apoderado (si dejó uno) con el motivo. */
    public void rechazada(Long matriculaId, String motivo) {
        MatriculaPublica mp = publicas.findById(matriculaId).orElse(null);
        if (mp == null) return;
        mp.setEstado("RECHAZADA");
        if (mp.getApoderadoEmail() == null) {
            mp.setCorreoEstado("OMITIDO");
        } else {
            Matricula m = matriculas.findById(matriculaId).orElseThrow();
            try {
                String cuerpo = "La matrícula de " + m.getAlumnoNombre() + " (" + m.getCodigo() + ") no fue aprobada. Motivo: " + motivo
                    + ". El colegio se comunicará contigo para devolver el pago de la matrícula.";
                notificaciones.enviar(new NotificacionesClient.Correo(mp.getApoderadoEmail(), "Matrícula no aprobada · " + props.getColegio(),
                    marco("Matrícula no aprobada", "<p>Hola " + esc(mp.getApoderadoNombre()) + ",</p><p>" + esc(cuerpo) + "</p>"), cuerpo, List.of()));
                mp.setCorreoEstado("ENVIADO");
            } catch (Exception e) {
                log.warn("No se pudo enviar el correo de rechazo de la matrícula {}: {}", m.getCodigo(), e.getMessage());
                mp.setCorreoEstado("ERROR");
            }
        }
        publicas.save(mp);
    }

    private void accesoYCorreo(MatriculaPublica mp, Matricula m) {
        crearCuentas(mp, m);
        publicas.save(mp);
        Resumen res = resumenAprobado(mp, m);
        enviarCorreoAprobacion(mp, m, res);
    }

    // ------------------------------------------------------------------ consultas

    public Resumen resumen(String referencia, String token) {
        MatriculaPublica mp = autorizar(referencia, token);
        return resumen(mp, matriculas.findById(mp.getMatriculaId()).orElseThrow());
    }

    public byte[] constancia(String referencia, String token) {
        MatriculaPublica mp = autorizar(referencia, token);
        if (!"APROBADA".equals(mp.getEstado())) throw new BusinessRuleException("La constancia se emite cuando la administración aprueba la matrícula.");
        Matricula m = matriculas.findById(mp.getMatriculaId()).orElseThrow();
        return pdf(mp, m, resumen(mp, m));
    }

    // ------------------------------------------------------------------ cuentas y correo

    private void crearCuentas(MatriculaPublica mp, Matricula m) {
        if (mp.getAlumnoUsuario() == null) {
            try {
                AuthClient.Cuenta c = auth.crear(new AuthClient.CuentaPersona(mp.getAlumnoNombres(), mp.getAlumnoApellidos(), mp.getAlumnoDocumento(), "ALUMNO", null));
                mp.setAlumnoUsuario(c.username());     // la cuenta ya existe: se registra aunque el vínculo falle
                alumnos.vincularAlumno(m.getAlumnoId(), new AlumnosClient.VinculoUsuario(c.id()));
            } catch (Exception e) {
                log.warn("Cuenta del alumno de la matrícula {} incompleta (revisar el vínculo en Cuentas de acceso): {}", m.getCodigo(), e.getMessage());
            }
        }
        if (mp.getApoderadoUsuarioId() == null && mp.getApoderadoUsuario() == null) {
            try {
                String[] partes = mp.getApoderadoNombre().split(" ", 2);
                String nombres = mp.getApoderadoNombres() != null ? mp.getApoderadoNombres() : partes[0];
                String apellidos = mp.getApoderadoApellidos() != null ? mp.getApoderadoApellidos() : (partes.length > 1 ? partes[1] : partes[0]);
                AuthClient.Cuenta c = auth.crear(new AuthClient.CuentaPersona(nombres, apellidos, mp.getApoderadoDocumento(), "APODERADO", mp.getApoderadoEmail()));
                mp.setApoderadoUsuario(c.username());
                mp.setApoderadoUsuarioId(c.id());
                alumnos.vincularApoderado(mp.getApoderadoId(), new AlumnosClient.VinculoUsuario(c.id()));
            } catch (Exception e) {
                log.warn("Cuenta del apoderado de la matrícula {} incompleta (revisar el vínculo en Cuentas de acceso): {}", m.getCodigo(), e.getMessage());
            }
        }
    }

    /** Pago recibido: aviso breve de que la matrícula está en revisión (todavía sin usuario ni clave). Sin correo del apoderado se omite. */
    private void enviarCorreoRecibido(MatriculaPublica mp, Matricula m, Resumen r) {
        if (mp.getApoderadoEmail() == null) {
            mp.setCorreoEstado("OMITIDO");
            return;
        }
        try {
            String cuerpo = "Recibimos el pago de la matrícula de " + r.alumno() + " (" + m.getCodigo() + ") por " + soles(r.montoMatricula())
                + (r.comprobante() == null ? "" : ", comprobante " + r.comprobante()) + ". La administración la revisará y, cuando la apruebe, te enviaremos otro correo con tu usuario y clave para ingresar.";
            notificaciones.enviar(new NotificacionesClient.Correo(mp.getApoderadoEmail(), "Pago recibido · matrícula en revisión · " + props.getColegio(),
                marco("Pago recibido", "<p>Hola " + esc(mp.getApoderadoNombre()) + ",</p><p>" + esc(cuerpo) + "</p><p style=\"font-size:13px;color:#4b5875\">Tu cupo está reservado mientras se revisa. Este pago fue simulado (demostración).</p>"),
                cuerpo, List.of()));
            mp.setCorreoEstado("ENVIADO");
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo de pago recibido de la matrícula {}: {}", m.getCodigo(), e.getMessage());
            mp.setCorreoEstado("ERROR");
        }
    }

    /** Matrícula aprobada: correo con los datos para iniciar sesión y la constancia en PDF. Sin correo del apoderado se omite. */
    private void enviarCorreoAprobacion(MatriculaPublica mp, Matricula m, Resumen r) {
        if (mp.getApoderadoEmail() == null) {
            mp.setCorreoEstado("OMITIDO");
            return;
        }
        try {
            byte[] pdf = pdf(mp, m, r);
            notificaciones.enviar(new NotificacionesClient.Correo(mp.getApoderadoEmail(),
                "Matrícula aprobada · " + r.alumno() + " · " + props.getColegio(), html(r), texto(r),
                List.of(new NotificacionesClient.Adjunto("Constancia-matricula-" + m.getCodigo() + ".pdf", "application/pdf", Base64.getEncoder().encodeToString(pdf)))));
            mp.setCorreoEstado("ENVIADO");
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo de aprobación de la matrícula {}: {}", m.getCodigo(), e.getMessage());
            mp.setCorreoEstado("ERROR");
        }
    }

    private String marco(String titulo, String cuerpoHtml) {
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;color:#14213d;max-width:620px;margin:auto\">"
            + "<div style=\"background:#1f3a87;color:#fff;padding:18px 22px;border-radius:12px 12px 0 0\"><strong style=\"font-size:18px\">" + esc(props.getColegio())
            + "</strong><br/><span>" + esc(titulo) + "</span></div>"
            + "<div style=\"border:1px solid #e3e8f3;border-top:0;padding:20px 22px;border-radius:0 0 12px 12px\">" + cuerpoHtml + "</div></div>";
    }

    // ------------------------------------------------------------------ armado del resumen

    private Resumen resumen(MatriculaPublica mp, Matricula m) {
        PlanificacionClient.Grado grado = planificacion.grado(m.getGradoId());
        PlanificacionClient.Seccion sec = planificacion.seccion(m.getSeccionId());
        List<PensionResumen> pensiones = List.of();
        BigDecimal total = BigDecimal.ZERO;
        List<ClaseResumen> horario = List.of();
        if ("APROBADA".equals(mp.getEstado())) {
            List<PagoClient.CuotaResumen> cuotas = esperarPensiones(m.getId());
            pensiones = cuotas.stream().filter(c -> "PENSION".equals(c.concepto())).map(c -> new PensionResumen(c.descripcion(), fechaCorta(c.fechaVencimiento()), c.montoTotal())).toList();
            total = pensiones.stream().map(PensionResumen::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
            horario = horario(sec.id());
        }
        String correoDestino = mp.getApoderadoEmail() == null ? null : enmascarar(mp.getApoderadoEmail());
        boolean aprobada = "APROBADA".equals(mp.getEstado());
        String cuentas = mp.getAlumnoUsuario() == null && aprobada
            ? "No pudimos crear las cuentas automáticamente: secretaría las creará y te avisará." : null;
        return new Resumen(mp.getEstado(), m.getCodigo(), m.getCodigo(), m.getAlumnoNombre(), mp.getAlumnoDocumento(), mp.getApoderadoNombre(), grado.nombre(),
            sec.nombre(), turnoTexto(sec.turno()), mp.getMontoMatricula(), mp.getComprobante(),
            mp.getPagadaEn() == null ? null : mp.getPagadaEn().format(FECHA_HORA), pensiones, total, horario, aprobada ? mp.getAlumnoUsuario() : null,
            aprobada ? mp.getApoderadoUsuario() : null, mp.getCorreoEstado(), correoDestino, cuentas);
    }

    /** Resumen completo para el correo de aprobación (el trámite se marca APROBADA justo después de enviarlo). */
    private Resumen resumenAprobado(MatriculaPublica mp, Matricula m) {
        String previo = mp.getEstado();
        mp.setEstado("APROBADA");
        try {
            return resumen(mp, m);
        } finally {
            mp.setEstado(previo);
        }
    }

    private List<ClaseResumen> horario(Long seccionId) {
        try {
            var h = planificacionClient.horario(seccionId);
            return h.clases().stream().sorted(Comparator.comparing((PlanificacionClient.Clase c) -> c.diaSemana()).thenComparing(PlanificacionClient.Clase::bloqueOrden))
                .map(c -> new ClaseResumen(DIAS[c.diaSemana()], c.horaInicio().substring(0, 5) + "–" + c.horaFin().substring(0, 5), c.cursoNombre(), c.docenteNombre())).toList();
        } catch (Exception e) {
            log.warn("Sin horario para la sección {}: {}", seccionId, e.getMessage());
            return List.of();
        }
    }

    private byte[] pdf(MatriculaPublica mp, Matricula m, Resumen r) {
        TreeMap<Short, String[]> filas = new TreeMap<>();
        TreeMap<Short, String> horas = new TreeMap<>();
        try {
            for (var c : planificacionClient.horario(m.getSeccionId()).clases()) {
                filas.computeIfAbsent(c.bloqueOrden(), k -> new String[5])[c.diaSemana() - 1] = c.cursoNombre();
                horas.put(c.bloqueOrden(), c.horaInicio().substring(0, 5) + "–" + c.horaFin().substring(0, 5));
            }
        } catch (Exception e) {
            log.warn("PDF sin horario de la sección {}: {}", m.getSeccionId(), e.getMessage());
        }
        List<PdfConstancia.FilaHorario> horario = filas.entrySet().stream().map(e -> new PdfConstancia.FilaHorario(horas.get(e.getKey()), e.getValue())).toList();
        List<PdfConstancia.Fila> pensiones = r.pensiones().stream().map(p -> new PdfConstancia.Fila(p.descripcion(), p.vence(), soles(p.monto()))).toList();
        return PdfConstancia.generar(new PdfConstancia.Datos(props.getColegio(), String.valueOf(LocalDate.now().getYear()), m.getCodigo(), LocalDate.now().format(FECHA),
            r.alumno(), r.alumnoDocumento(), r.grado(), r.seccion(), r.turno(), r.apoderado(), mp.getApoderadoDocumento(),
            mp.getApoderadoEmail(), soles(r.montoMatricula()), r.comprobante(), "Pago en línea (simulado)", r.pagadoEn(), pensiones, soles(r.totalPensiones()),
            horario, r.usuarioAlumno(), r.usuarioApoderado()));
    }

    // ------------------------------------------------------------------ correo (HTML y texto)

    private String html(Resumen r) {
        StringBuilder h = new StringBuilder();
        h.append("<div style=\"font-family:Arial,Helvetica,sans-serif;color:#14213d;max-width:620px;margin:auto\">")
            .append("<div style=\"background:#1f3a87;color:#fff;padding:18px 22px;border-radius:12px 12px 0 0\"><strong style=\"font-size:18px\">")
            .append(esc(props.getColegio())).append("</strong><br/><span>Matrícula aprobada</span></div>")
            .append("<div style=\"border:1px solid #e3e8f3;border-top:0;padding:20px 22px;border-radius:0 0 12px 12px\">")
            .append("<p>Hola ").append(esc(r.apoderado())).append(", la administración <strong>aprobó</strong> la matrícula de <strong>").append(esc(r.alumno())).append("</strong>. Ya pueden ingresar al portal.</p>")
            .append("<h3 style=\"margin:18px 0 6px\">Matrícula</h3><p style=\"margin:0\">Código <strong>").append(esc(r.codigo())).append("</strong> · ")
            .append(esc(r.grado())).append(" · sección ").append(esc(r.seccion())).append(" · turno ").append(esc(r.turno())).append("</p>")
            .append("<h3 style=\"margin:18px 0 6px\">Finanzas</h3><p style=\"margin:0\">Matrícula pagada: <strong>").append(soles(r.montoMatricula())).append("</strong>")
            .append(r.comprobante() == null ? "" : " · comprobante " + esc(r.comprobante())).append("</p>");
        if (!r.pensiones().isEmpty()) {
            h.append("<p style=\"margin:10px 0 4px\">Calendario de pensiones (total ").append(soles(r.totalPensiones())).append("):</p><table style=\"border-collapse:collapse;width:100%;font-size:14px\">");
            for (PensionResumen p : r.pensiones()) {
                h.append("<tr><td style=\"padding:4px 8px;border-bottom:1px solid #eef1f8\">").append(esc(p.descripcion())).append("</td><td style=\"padding:4px 8px;border-bottom:1px solid #eef1f8\">vence ")
                    .append(esc(p.vence())).append("</td><td style=\"padding:4px 8px;border-bottom:1px solid #eef1f8;text-align:right\">").append(soles(p.monto())).append("</td></tr>");
            }
            h.append("</table><p style=\"font-size:13px;color:#4b5875\">Puedes pagar en el portal (Pagos) o en caja del colegio.</p>");
        }
        h.append("<h3 style=\"margin:18px 0 6px\">Para iniciar sesión</h3><div style=\"background:#eef3ff;border:1px solid #c9d6f5;border-radius:10px;padding:12px 14px;font-size:15px\">")
            .append("Portal: <a href=\"").append(esc(props.getPortalUrl())).append("\">").append(esc(props.getPortalUrl())).append("</a><br/>")
            .append("Alumno · usuario: <strong>").append(r.usuarioAlumno() == null ? "lo creará secretaría" : esc(r.usuarioAlumno())).append("</strong> · clave inicial: su número de documento<br/>")
            .append("Apoderado · usuario: <strong>").append(r.usuarioApoderado() == null ? "ya tenías cuenta o la creará secretaría" : esc(r.usuarioApoderado())).append("</strong> · clave inicial: su número de documento</div>")
            .append("<p style=\"font-size:13px;color:#4b5875;margin:8px 0 0\">Por seguridad, el sistema te pedirá crear una clave nueva en tu primer ingreso. También puedes usar la app móvil del colegio con los mismos datos.</p>");
        if (!r.horario().isEmpty()) {
            h.append("<h3 style=\"margin:18px 0 6px\">Horario</h3><p style=\"margin:0;font-size:14px\">Está en la constancia adjunta (PDF). Primeras clases: ");
            r.horario().stream().limit(3).forEach(c -> h.append(esc(c.dia())).append(' ').append(esc(c.hora())).append(" ").append(esc(c.curso())).append("; "));
            h.append("</p>");
        } else {
            h.append("<h3 style=\"margin:18px 0 6px\">Horario</h3><p style=\"margin:0\">Todavía no está publicado; te avisaremos por el portal.</p>");
        }
        h.append("<h3 style=\"margin:18px 0 6px\">Pendiente</h3><p style=\"margin:0\">Presenta en secretaría copia del DNI del alumno, partida de nacimiento y ficha SIAGIE.</p>")
            .append("<p style=\"margin-top:20px;font-size:12px;color:#6b7694\">Adjuntamos la constancia de matrícula en PDF. Este pago fue simulado (demostración).</p></div></div>");
        return h.toString();
    }

    private String texto(Resumen r) {
        return "Matrícula aprobada: " + r.alumno() + " (" + r.codigo() + ") · " + r.grado() + " · sección " + r.seccion() + ". Matrícula pagada: "
            + soles(r.montoMatricula()) + ". Usuario del alumno: " + (r.usuarioAlumno() == null ? "lo creará secretaría" : r.usuarioAlumno())
            + ". Apoderado: " + (r.usuarioApoderado() == null ? "ya tenías cuenta o la creará secretaría" : r.usuarioApoderado())
            + ". La clave inicial de cada uno es su número de documento y el sistema pedirá cambiarla. Portal: " + props.getPortalUrl() + ". Adjuntamos la constancia en PDF.";
    }

    // ------------------------------------------------------------------ utilidades

    private PagoClient.OfertaCosto costo(Long anioId, Long nivelId, Long gradoId) {
        try {
            return pago.tarifa(anioId, nivelId, gradoId);
        } catch (Exception e) {
            log.warn("Sin tarifas para el grado {}: {}", gradoId, e.getMessage());
            return null;
        }
    }

    private PagoClient.CuotaResumen esperarCuotaMatricula(Long matriculaId) {
        for (int i = 0; i < 25; i++) {
            try {
                var c = pago.cuotas(matriculaId).stream().filter(x -> "MATRICULA".equals(x.concepto())).findFirst();
                if (c.isPresent()) return c.get();
            } catch (Exception e) {
                log.debug("Esperando la cuota de la matrícula {}: {}", matriculaId, e.getMessage());
            }
            dormir(400);
        }
        return null;
    }

    /** Las pensiones las genera pago-ms al recibir matricula.confirmada (asíncrono): se espera unos segundos. */
    private List<PagoClient.CuotaResumen> esperarPensiones(Long matriculaId) {
        List<PagoClient.CuotaResumen> ultimas = List.of();
        for (int i = 0; i < 20; i++) {
            try {
                ultimas = pago.cuotas(matriculaId);
                if (ultimas.stream().anyMatch(c -> "PENSION".equals(c.concepto()))) return ultimas;
            } catch (Exception e) {
                log.debug("Esperando las pensiones de la matrícula {}: {}", matriculaId, e.getMessage());
            }
            dormir(400);
        }
        return ultimas;
    }

    private MatriculaPublica autorizar(String referencia, String token) {
        Matricula m = matriculas.findByCodigo(referencia == null ? "" : referencia.trim()).orElse(null);
        MatriculaPublica mp = m == null ? null : publicas.findById(m.getId()).orElse(null);
        boolean valido = mp != null && token != null && MessageDigest.isEqual(hash(token).getBytes(StandardCharsets.UTF_8), mp.getTokenHash().getBytes(StandardCharsets.UTF_8));
        if (!valido) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El enlace de la matrícula no es válido.");
        return mp;
    }

    private String turno(Long seccionId) {
        return turnoTexto(planificacion.seccion(seccionId).turno());
    }

    private static String turnoTexto(String t) {
        return "TARDE".equalsIgnoreCase(t) ? "tarde" : "mañana";
    }

    private static String generarToken() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String def(String tipo) {
        return tipo == null || tipo.isBlank() ? "DNI" : tipo;
    }

    private static String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String fechaCorta(String iso) {
        try {
            return LocalDate.parse(iso).format(FECHA);
        } catch (Exception e) {
            return iso;
        }
    }

    static String soles(BigDecimal v) {
        return "S/ " + (v == null ? "0.00" : v.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    static String enmascarar(String correo) {
        int i = correo.indexOf('@');
        return i <= 1 ? "***" : correo.charAt(0) + "***" + correo.substring(i);
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
