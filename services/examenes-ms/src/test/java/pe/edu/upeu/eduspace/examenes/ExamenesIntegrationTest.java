package pe.edu.upeu.eduspace.examenes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.upeu.eduspace.examenes.client.AlumnosClient;
import pe.edu.upeu.eduspace.examenes.client.PlanificacionClient;
import pe.edu.upeu.eduspace.examenes.dto.*;
import pe.edu.upeu.eduspace.examenes.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.examenes.messaging.MatriculaEventConsumer;
import pe.edu.upeu.eduspace.examenes.scheduler.CierreExamenJob;
import pe.edu.upeu.eduspace.examenes.scheduler.CierreIntentosJob;
import pe.edu.upeu.eduspace.examenes.service.*;

@AutoConfigureMockMvc
class ExamenesIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired MatriculaEventConsumer consumer;
    @Autowired PreguntaService preguntas;
    @Autowired ExamenService examenes;
    @Autowired IntentoService intentos;
    @Autowired RespuestaService respuestas;
    @Autowired RevisionService revision;
    @Autowired CierreIntentosJob cierreIntentos;
    @Autowired CierreExamenJob cierreExamen;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @MockitoBean PlanificacionClient planificacion;
    @MockitoBean AlumnosClient alumnosClient;
    @MockitoBean KafkaTemplate<String, String> kafka;

    static final long SECCION = 10, CURSO = 7, ANIO = 1, PERIODO = 3, DOCENTE = 50, U_DOCENTE = 5;

    long p1, p2, p3, p4;       // unica, unica, multiple, abierta

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE respuestas, intentos, examen_preguntas, examenes, opciones_pregunta, preguntas, matriculas_local, eventos_procesados RESTART IDENTITY CASCADE");
        reset(planificacion, alumnosClient, kafka);
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        when(planificacion.getDocentePorUsuario(U_DOCENTE)).thenReturn(new PlanificacionClient.DocenteInterno(DOCENTE, "Prof. Díaz", "ACTIVO"));
        when(planificacion.getDocentePorUsuario(6L)).thenReturn(new PlanificacionClient.DocenteInterno(60L, "Otro", "ACTIVO"));
        when(planificacion.validarAsignacionDocente(DOCENTE, SECCION, CURSO)).thenReturn(true);
        when(planificacion.validarAsignacionDocente(60L, SECCION, CURSO)).thenReturn(false);
        when(planificacion.getCursoPorId(CURSO)).thenReturn(Map.of("id", CURSO, "nombre", "Matemática"));
        for (long a = 1; a <= 3; a++) matricula(a, a, SECCION, "MATRICULADO");
        when(alumnosClient.alumnoDeUsuario(301L)).thenReturn(new AlumnosClient.AlumnoUsuario(1L));
        when(alumnosClient.alumnoDeUsuario(302L)).thenReturn(new AlumnosClient.AlumnoUsuario(2L));
        when(alumnosClient.hijosDeUsuario(700L)).thenReturn(List.of(new AlumnosClient.Hijo(1L, "A-1", "Alumno 1")));

        p1 = pregunta("OPCION_UNICA", "2+2", "4", "5");
        p2 = pregunta("OPCION_UNICA", "3+3", "6", "7");
        p3 = pregunta("OPCION_MULTIPLE", "Números pares", "2,4", "3");
        p4 = pregunta("ABIERTA", "Explique la suma", null);
    }

    void matricula(long matriculaId, long alumnoId, long seccionId, String estado) {
        consumer.procesarEventoMatricula("""
            {"eventId":"%s","eventType":"matricula.confirmada","occurredAt":"2026-02-01T10:00:00","version":1,
             "matriculaId":%d,"alumnoId":%d,"alumnoNombre":"Alumno %d","anioId":1,"nivelId":1,"gradoId":3,"seccionId":%d,"estado":"%s"}
            """.formatted(UUID.randomUUID(), matriculaId, alumnoId, alumnoId, seccionId, estado));
    }

    /** Crea una pregunta; las opciones correctas van en la lista separada por coma (la primera cadena es la correcta). */
    long pregunta(String tipo, String enunciado, String correctas, String... incorrectas) {
        PreguntaRequest r = new PreguntaRequest();
        r.setCursoId(CURSO); r.setTema("Aritmética"); r.setTipo(tipo); r.setEnunciado(enunciado); r.setPuntaje(new BigDecimal("2"));
        if (correctas != null) {
            List<OpcionRequest> ops = new java.util.ArrayList<>();
            short orden = 1;
            for (String c : correctas.split(",")) ops.add(OpcionRequest.builder().texto(c).esCorrecta(true).orden(orden++).build());
            for (String i : incorrectas) ops.add(OpcionRequest.builder().texto(i).esCorrecta(false).orden(orden++).build());
            r.setOpciones(ops);
        }
        return preguntas.crear(r, DOCENTE).getId();
    }

    ExamenCreateRequest solicitud(boolean conAbierta) {
        ExamenCreateRequest r = new ExamenCreateRequest();
        r.setAnioId(ANIO); r.setPeriodoId(PERIODO); r.setSeccionId(SECCION); r.setCursoId(CURSO);
        r.setTitulo("Examen parcial"); r.setFechaInicio(LocalDateTime.now().minusHours(1)); r.setFechaFin(LocalDateTime.now().plusHours(3));
        r.setDuracionMin((short) 60); r.setIntentosMax((short) 1); r.setBarajarPreguntas(true);
        List<ExamenPreguntaItem> items = new java.util.ArrayList<>();
        short orden = 1;
        for (long id : conAbierta ? new long[]{p1, p2, p3, p4} : new long[]{p1, p2, p3}) {
            ExamenPreguntaItem it = new ExamenPreguntaItem();
            it.setPreguntaId(id); it.setOrden(orden++); it.setPuntaje(new BigDecimal("2"));
            items.add(it);
        }
        r.setPreguntas(items);
        return r;
    }

    long examenPublicado(boolean conAbierta) {
        long id = examenes.crear(solicitud(conAbierta), DOCENTE).getId();
        examenes.publicar(id, DOCENTE);
        return id;
    }

    List<Long> opciones(long preguntaId, boolean correctas) {
        return jdbc.queryForList("select id from opciones_pregunta where pregunta_id = ? and es_correcta = ? order by orden", Long.class, preguntaId, correctas);
    }

    void responder(long intentoId, long alumnoId, boolean todoBien) {
        GuardarRespuestaRequest a = new GuardarRespuestaRequest();
        a.setOpcionesIds(todoBien ? opciones(p1, true) : opciones(p1, false));
        respuestas.guardarRespuestaIncremental(intentoId, p1, a, alumnoId);
        GuardarRespuestaRequest b = new GuardarRespuestaRequest();
        b.setOpcionesIds(opciones(p2, true));
        respuestas.guardarRespuestaIncremental(intentoId, p2, b, alumnoId);
        GuardarRespuestaRequest c = new GuardarRespuestaRequest();
        c.setOpcionesIds(todoBien ? opciones(p3, true) : opciones(p3, false));
        respuestas.guardarRespuestaIncremental(intentoId, p3, c, alumnoId);
    }

    // ------------------------------------------------------------------ gestión del examen

    @Test
    void docenteAsignadoCreaBorradorConPuntajeTotal() {
        ExamenResponse e = examenes.crear(solicitud(false), DOCENTE);
        assertThat(e.getEstado()).isEqualTo("BORRADOR");
        assertThat(e.getPuntajeTotal()).isEqualByComparingTo("6");
        assertThat(e.getDocenteId()).isEqualTo(DOCENTE);
    }

    @Test
    void docenteSinAsignacionNoCreaExamenes() {
        assertThatThrownBy(() -> examenes.crear(solicitud(false), 60L)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("asignación");
    }

    @Test
    void editarEsPosibleSoloEnBorradorYSoloPorElDuenio() {
        long id = examenes.crear(solicitud(false), DOCENTE).getId();
        ExamenCreateRequest cambio = solicitud(true);
        cambio.setTitulo("Examen parcial (editado)");
        ExamenResponse e = examenes.actualizar(id, cambio, DOCENTE);
        assertThat(e.getTitulo()).contains("editado");
        assertThat(e.getPuntajeTotal()).isEqualByComparingTo("8");
        assertThat(jdbc.queryForObject("select count(*) from examen_preguntas where examen_id = ?", Integer.class, id)).isEqualTo(4);
        assertThatThrownBy(() -> examenes.actualizar(id, cambio, 60L)).isInstanceOf(AccessDeniedException.class);
        examenes.publicar(id, DOCENTE);
        assertThatThrownBy(() -> examenes.actualizar(id, cambio, DOCENTE)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("BORRADOR");
    }

    @Test
    void publicarValidaDuenioVentanaYEmiteEventoPlanoConLosAlumnos() {
        long id = examenes.crear(solicitud(false), DOCENTE).getId();
        assertThatThrownBy(() -> examenes.publicar(id, 60L)).isInstanceOf(AccessDeniedException.class);

        ExamenCreateRequest vencida = solicitud(false);
        vencida.setFechaInicio(LocalDateTime.now().minusDays(2));
        vencida.setFechaFin(LocalDateTime.now().minusDays(1));
        long vencidoId = examenes.crear(vencida, DOCENTE).getId();
        assertThatThrownBy(() -> examenes.publicar(vencidoId, DOCENTE)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("terminó");

        examenes.publicar(id, DOCENTE);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("examen.publicado"), anyString(), json.capture());
        assertThat(json.getValue()).contains("\"eventType\":\"examen.publicado\"").contains("\"cursoNombre\":\"Matemática\"")
            .contains("\"alumnoIds\":[1,2,3]").doesNotContain("\"payload\"");
    }

    // ------------------------------------------------------------------ rendición

    @Test
    void elAlumnoNuncaRecibeLaRespuestaCorrectaYElIntentoSeReanuda() {
        long id = examenPublicado(false);
        IntentoIniciadoResponse i1 = intentos.iniciarIntento(id, 1L);
        assertThat(i1.getPreguntas()).hasSize(3);
        assertThat(i1.getVenceEn()).isBefore(LocalDateTime.now().plusMinutes(61));
        String json = new tools.jackson.databind.ObjectMapper().writeValueAsString(i1);
        assertThat(json).doesNotContain("esCorrecta").doesNotContain("correcta");
        IntentoIniciadoResponse i2 = intentos.iniciarIntento(id, 1L);          // reanuda
        assertThat(i2.getIntentoId()).isEqualTo(i1.getIntentoId());
        assertThat(jdbc.queryForObject("select count(*) from intentos", Integer.class)).isEqualTo(1);
    }

    @Test
    void laBaseImpideDosIntentosEnCursoDelMismoExamen() {
        long id = examenPublicado(false);
        IntentoIniciadoResponse i = intentos.iniciarIntento(id, 1L);
        assertThatThrownBy(() -> jdbc.update("""
            insert into intentos (examen_id, matricula_id, alumno_id, numero, iniciado_en, vence_en, estado)
            values (?, 1, 1, 2, now(), now() + interval '1 hour', 'EN_CURSO')
            """, id)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(i.getIntentoId()).isNotNull();
    }

    @Test
    void alumnoDeOtraSeccionNoRinde() {
        long id = examenPublicado(false);
        matricula(9, 9, 11, "MATRICULADO");
        assertThatThrownBy(() -> intentos.iniciarIntento(id, 9L)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("no está matriculado");
    }

    @Test
    void guardadoIncrementalEsUpsertYLaPreguntaDebeSerDelExamen() {
        long id = examenPublicado(false);
        long intento = intentos.iniciarIntento(id, 1L).getIntentoId();
        GuardarRespuestaRequest r = new GuardarRespuestaRequest();
        r.setOpcionesIds(opciones(p1, false));
        respuestas.guardarRespuestaIncremental(intento, p1, r, 1L);
        r.setOpcionesIds(opciones(p1, true));                                   // cambia de opinión
        respuestas.guardarRespuestaIncremental(intento, p1, r, 1L);
        assertThat(jdbc.queryForObject("select count(*) from respuestas", Integer.class)).isEqualTo(1);

        long fuera = pregunta("OPCION_UNICA", "No está en el examen", "x", "y");
        assertThatThrownBy(() -> respuestas.guardarRespuestaIncremental(intento, fuera, r, 1L))
            .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no pertenece");
        assertThatThrownBy(() -> respuestas.guardarRespuestaIncremental(intento, p1, r, 2L))
            .isInstanceOf(BusinessRuleException.class).hasMessageContaining("autorizado");
    }

    @Test
    void enviarCalificaAutomaticamenteConNotaSobre20YPublicaElEventoConPeriodoYCurso() {
        long id = examenPublicado(false);
        long intento = intentos.iniciarIntento(id, 1L).getIntentoId();
        responder(intento, 1L, true);
        IntentoResultadoResponse res = intentos.enviarIntento(intento, 1L);
        assertThat(res.getEstado()).isEqualTo("CALIFICADO");
        assertThat(res.getPuntajeObtenido()).isEqualByComparingTo("6");
        assertThat(res.getNota()).isEqualByComparingTo("20.00");
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("examen.calificado"), anyString(), json.capture());
        assertThat(json.getValue()).contains("\"periodoId\":3").contains("\"cursoId\":7").contains("\"matriculaId\":1")
            .contains("\"cursoNombre\":\"Matemática\"").contains("\"nota\":20.00").contains("\"alumnoId\":1").doesNotContain("\"payload\"");
        assertThatThrownBy(() -> intentos.enviarIntento(intento, 1L)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void multipleEsTodoONadaYNotaParcial() {
        long id = examenPublicado(false);
        long intento = intentos.iniciarIntento(id, 1L).getIntentoId();
        responder(intento, 1L, false);                           // p1 y p3 mal, p2 bien -> 2 de 6
        IntentoResultadoResponse res = intentos.enviarIntento(intento, 1L);
        assertThat(res.getPuntajeObtenido()).isEqualByComparingTo("2");
        assertThat(res.getNota()).isEqualByComparingTo("6.67");
    }

    @Test
    void conPreguntaAbiertaQuedaPendienteHastaQueElDocenteRevisa() {
        long id = examenPublicado(true);
        long intento = intentos.iniciarIntento(id, 1L).getIntentoId();
        responder(intento, 1L, true);
        GuardarRespuestaRequest abierta = new GuardarRespuestaRequest();
        abierta.setTextoRespuesta("Sumar es juntar cantidades");
        respuestas.guardarRespuestaIncremental(intento, p4, abierta, 1L);
        IntentoResultadoResponse res = intentos.enviarIntento(intento, 1L);
        assertThat(res.isRequiereRevisionAbiertas()).isTrue();
        assertThat(res.getNota()).isNull();
        verify(kafka, never()).send(eq("examen.calificado"), anyString(), anyString());

        long respuestaId = jdbc.queryForObject("select id from respuestas where pregunta_id = ?", Long.class, p4);
        RevisionRequest rev = new RevisionRequest();
        rev.setPuntajeObtenido(new BigDecimal("1"));
        revision.revisarRespuestaAbierta(respuestaId, rev, DOCENTE);
        assertThat(jdbc.queryForObject("select nota from intentos", BigDecimal.class)).isEqualByComparingTo("17.50");   // 7 de 8
        verify(kafka, times(1)).send(eq("examen.calificado"), anyString(), anyString());
    }

    @Test
    void sinIntentosDisponiblesYaNoSeRinde() {
        long id = examenPublicado(false);
        long intento = intentos.iniciarIntento(id, 1L).getIntentoId();
        intentos.enviarIntento(intento, 1L);
        assertThatThrownBy(() -> intentos.iniciarIntento(id, 1L)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("límite");
    }

    // ------------------------------------------------------------------ cierre

    @Test
    void cerrarManualCierraYCalificaLosIntentosAbiertosYSoloLoHaceElDuenio() {
        long id = examenPublicado(false);
        long intento = intentos.iniciarIntento(id, 1L).getIntentoId();
        responder(intento, 1L, true);
        assertThatThrownBy(() -> examenes.cerrar(id, 60L)).isInstanceOf(AccessDeniedException.class);
        examenes.cerrar(id, DOCENTE);
        assertThat(jdbc.queryForObject("select estado from examenes", String.class)).isEqualTo("CERRADO");
        assertThat(jdbc.queryForObject("select estado from intentos", String.class)).isEqualTo("CALIFICADO");
        assertThat(jdbc.queryForObject("select nota from intentos", BigDecimal.class)).isEqualByComparingTo("20.00");
        assertThatThrownBy(() -> examenes.cerrar(id, DOCENTE)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> intentos.iniciarIntento(id, 2L)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void elJobCierraLosIntentosVencidosPorElTiempoDelServidor() {
        long id = examenPublicado(false);
        long intento = intentos.iniciarIntento(id, 1L).getIntentoId();
        responder(intento, 1L, true);
        jdbc.update("update intentos set vence_en = now() - interval '5 minutes'");
        assertThat(cierreIntentos.ejecutar()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select estado from intentos", String.class)).isEqualTo("CALIFICADO");
        assertThat(cierreIntentos.ejecutar()).isZero();
    }

    @Test
    void elJobCierraLosExamenesCuyaVentanaTermino() {
        long id = examenPublicado(false);
        intentos.iniciarIntento(id, 1L);
        jdbc.update("update examenes set fecha_fin = now() - interval '1 minute'");
        jdbc.update("update intentos set vence_en = now() - interval '1 minute'");
        assertThat(cierreExamen.ejecutar()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select estado from examenes", String.class)).isEqualTo("CERRADO");
        assertThat(jdbc.queryForObject("select estado from intentos", String.class)).isEqualTo("CALIFICADO");
    }

    @Test
    void bancoDePreguntasSeConsultaConYSinFiltros() throws Exception {
        mvc.perform(get("/api/v1/examenes/preguntas").param("cursoId", "7").with(usuario(U_DOCENTE, "DOCENTE")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.contenido.length()").value(4));
        mvc.perform(get("/api/v1/examenes/preguntas").param("cursoId", "7").param("tema", "arit").param("tipo", "ABIERTA").with(usuario(U_DOCENTE, "DOCENTE")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.contenido.length()").value(1));
    }

    @Test
    void estadisticasDelExamenParaElDocente() throws Exception {
        long id = examenPublicado(false);
        long i1 = intentos.iniciarIntento(id, 1L).getIntentoId();
        responder(i1, 1L, true);
        intentos.enviarIntento(i1, 1L);
        long i2 = intentos.iniciarIntento(id, 2L).getIntentoId();
        responder(i2, 2L, false);
        intentos.enviarIntento(i2, 2L);
        mvc.perform(get("/api/v1/examenes/" + id + "/resultados").with(usuario(U_DOCENTE, "DOCENTE")))
            .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ API y seguridad

    JwtRequestPostProcessor usuario(long id, String rol) {
        return jwt().jwt(j -> j.claim("usuarioId", id).claim("realm_access", Map.of("roles", List.of(rol))))
            .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @Test
    void elResultadoSoloLoVeSuDuenioSuApoderadoOElDocente() throws Exception {
        long id = examenPublicado(false);
        long intento = intentos.iniciarIntento(id, 1L).getIntentoId();
        intentos.enviarIntento(intento, 1L);
        String url = "/api/v1/examenes/intentos/" + intento + "/resultado";
        mvc.perform(get(url).with(usuario(301, "ALUMNO"))).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CALIFICADO"));
        mvc.perform(get(url).with(usuario(700, "APODERADO"))).andExpect(status().isOk());
        mvc.perform(get(url).with(usuario(302, "ALUMNO"))).andExpect(status().isForbidden());          // IDOR bloqueado
        mvc.perform(get(url).with(usuario(U_DOCENTE, "DOCENTE"))).andExpect(status().isOk());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    void misIntentosUsaLaIdentidadRealDelAlumnoYDeLosHijos() throws Exception {
        long id = examenPublicado(false);
        long intento = intentos.iniciarIntento(id, 1L).getIntentoId();
        intentos.enviarIntento(intento, 1L);
        mvc.perform(get("/api/v1/examenes/mias").with(usuario(301, "ALUMNO")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/examenes/mias").with(usuario(700, "APODERADO")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/examenes/mias").with(usuario(302, "ALUMNO")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void examenesDisponiblesParaElAlumnoAutenticado() throws Exception {
        examenPublicado(false);
        mvc.perform(get("/api/v1/examenes/disponibles").with(usuario(301, "ALUMNO")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].puedeRendir").value(true));
        mvc.perform(get("/api/v1/examenes/disponibles").with(usuario(U_DOCENTE, "DOCENTE"))).andExpect(status().isForbidden());
    }

    @Test
    void crearEditarPublicarCerrarPorApiConRolesYPropiedad() throws Exception {
        String body = """
            {"anioId":1,"periodoId":3,"seccionId":10,"cursoId":7,"titulo":"Por API","duracionMin":30,"intentosMax":1,
             "fechaInicio":"%s","fechaFin":"%s","preguntas":[{"preguntaId":%d,"orden":1,"puntaje":2}]}
            """.formatted(LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(2), p1);
        mvc.perform(post("/api/v1/examenes").with(usuario(301, "ALUMNO")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/examenes").with(usuario(6, "DOCENTE")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());   // sin asignación
        String creado = mvc.perform(post("/api/v1/examenes").with(usuario(U_DOCENTE, "DOCENTE")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(creado, "$.id")).longValue();
        mvc.perform(put("/api/v1/examenes/" + id).with(usuario(U_DOCENTE, "DOCENTE")).contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("Por API", "Por API v2")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.titulo").value("Por API v2"));
        mvc.perform(put("/api/v1/examenes/" + id).with(usuario(6, "DOCENTE")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/examenes/" + id + "/publicar").with(usuario(6, "DOCENTE"))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/examenes/" + id + "/publicar").with(usuario(U_DOCENTE, "DOCENTE"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("PUBLICADO"));
        mvc.perform(patch("/api/v1/examenes/" + id + "/cerrar").with(usuario(U_DOCENTE, "DOCENTE"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("CERRADO"));
        mvc.perform(get("/api/v1/examenes/" + id).with(usuario(301, "ALUMNO"))).andExpect(status().isForbidden());   // detalle con respuestas: solo docente
    }
}
