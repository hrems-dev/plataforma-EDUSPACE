package pe.edu.eduspace.planificacion.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Recorrido completo de la API con PostgreSQL real, seguridad JWT y captura de eventos. */
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlanificacionApiIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired MockMvc mvc;
    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;

    // ids compartidos entre pasos (el orden de los métodos es fijo)
    static long anioId, periodoId, aulaId, gradoId, seccionAId, seccionBId, docenteId, docente2Id, cursoMatId, cursoComId,
            asignacionId, asignacionBId, bloque1Id, bloque2Id, recesoId, bloqueTardeId, horarioId;

    JwtRequestPostProcessor usuario(long usuarioId, String rol) {
        return jwt().jwt(j -> j.claim("usuarioId", usuarioId).claim("realm_access", java.util.Map.of("roles", List.of(rol))))
                .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    JwtRequestPostProcessor admin() {
        return usuario(1L, "ADMIN");
    }

    ResultActions enviar(String metodo, String ruta, Object cuerpo, JwtRequestPostProcessor quien) throws Exception {
        var req = switch (metodo) {
            case "POST" -> post(ruta);
            case "PUT" -> put(ruta);
            case "PATCH" -> patch(ruta);
            default -> throw new IllegalArgumentException(metodo);
        };
        return mvc.perform(req.with(quien).contentType(MediaType.APPLICATION_JSON).content(cuerpo == null ? "" : cuerpo.toString()));
    }

    long crear(String ruta, String json) throws Exception {
        String body = enviar("POST", ruta, json, admin()).andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    @Order(1)
    void seguridad_sinTokenEs401_yLaEscrituraExigeRol() throws Exception {
        mvc.perform(get("/api/v1/planificacion/anios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/planificacion/anios").with(usuario(9L, "ALUMNO"))).andExpect(status().isOk());
        enviar("POST", "/api/v1/planificacion/aulas", "{\"codigo\":\"X\",\"nombre\":\"X\",\"capacidad\":10}", usuario(9L, "ALUMNO"))
                .andExpect(status().isForbidden());
        // SECRETARIA solo escribe horarios, asignaciones y docentes
        enviar("POST", "/api/v1/planificacion/aulas", "{\"codigo\":\"X\",\"nombre\":\"X\",\"capacidad\":10}", usuario(2L, "SECRETARIA"))
                .andExpect(status().isForbidden());
        enviar("POST", "/api/v1/planificacion/anios", "{\"nombre\":\"X\",\"fechaInicio\":\"2026-03-01\",\"fechaFin\":\"2026-12-01\"}", usuario(2L, "SECRETARIA"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(2)
    void anio_soloUnoActivo_yPublicaEvento() throws Exception {
        reset(kafkaTemplate);
        when(kafkaTemplate.send(any(String.class), any(String.class), any(String.class))).thenReturn(CompletableFuture.completedFuture(null));

        anioId = crear("/api/v1/planificacion/anios", "{\"nombre\":\"2026\",\"fechaInicio\":\"2026-03-01\",\"fechaFin\":\"2026-12-20\"}");
        long otroId = crear("/api/v1/planificacion/anios", "{\"nombre\":\"2027\",\"fechaInicio\":\"2027-03-01\",\"fechaFin\":\"2027-12-20\"}");
        mvc.perform(patch("/api/v1/planificacion/anios/" + anioId + "/activar").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ACTIVO"));
        mvc.perform(patch("/api/v1/planificacion/anios/" + otroId + "/activar").with(admin()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("REGLA_DE_NEGOCIO"))
                .andExpect(jsonPath("$.mensaje", containsString("ciérrelo")));
        mvc.perform(get("/api/v1/planificacion/anios/activo").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("2026"));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, timeout(2000)).send(eq("anio.activado"), eq(String.valueOf(anioId)), json.capture());
        assertThat(json.getValue()).contains("\"nombre\":\"2026\"");
    }

    @Test
    @Order(3)
    void periodos_sinSolapamientoYDentroDelAnio() throws Exception {
        periodoId = crear("/api/v1/planificacion/periodos", "{\"anioId\":" + anioId + ",\"numero\":1,\"nombre\":\"I Bimestre\",\"fechaInicio\":\"2026-03-01\",\"fechaFin\":\"2026-05-15\"}");
        enviar("POST", "/api/v1/planificacion/periodos", "{\"anioId\":" + anioId + ",\"numero\":2,\"nombre\":\"II\",\"fechaInicio\":\"2026-05-10\",\"fechaFin\":\"2026-07-30\"}", admin())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("solapan")));
        enviar("POST", "/api/v1/planificacion/periodos", "{\"anioId\":" + anioId + ",\"numero\":2,\"nombre\":\"II\",\"fechaInicio\":\"2026-11-01\",\"fechaFin\":\"2027-02-01\"}", admin())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("dentro del rango")));
        enviar("POST", "/api/v1/planificacion/periodos", "{\"anioId\":" + anioId + ",\"numero\":1,\"nombre\":\"Repetido\",\"fechaInicio\":\"2026-06-01\",\"fechaFin\":\"2026-07-30\"}", admin())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("número 1")));
        enviar("PATCH", "/api/v1/planificacion/periodos/" + periodoId + "/estado", "{\"estado\":\"ACTIVO\"}", admin())
                .andExpect(status().isOk());
        verify(kafkaTemplate, timeout(2000)).send(eq("periodo.cambiado"), eq(String.valueOf(periodoId)), any(String.class));
    }

    @Test
    @Order(4)
    void rutaInexistenteEs404YMetodoNoPermitidoEs405_noEs500() throws Exception {
        mvc.perform(get("/api/v1/planificacion/no-existe").with(admin())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/planificacion/anios").with(admin())).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
        mvc.perform(get("/api/v1/planificacion/anios/abc").with(admin())).andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    void validacionDeEntradaDevuelve400ConDetalle() throws Exception {
        enviar("POST", "/api/v1/planificacion/aulas", "{\"codigo\":\"\",\"nombre\":\"\",\"capacidad\":0}", admin())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles").isNotEmpty()).andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @Order(5)
    void estructura_aulaSeccionCapacidadYMalla() throws Exception {
        String grados = mvc.perform(get("/api/v1/planificacion/grados").with(admin())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        gradoId = ((Number) ((List<?>) JsonPath.read(grados, "$[?(@.nivelNombre=='Primaria' && @.numero==1)].id")).get(0)).longValue();

        aulaId = crear("/api/v1/planificacion/aulas", "{\"codigo\":\"A-101\",\"nombre\":\"Aula 101\",\"tipo\":\"AULA\",\"capacidad\":30}");
        enviar("POST", "/api/v1/planificacion/secciones", "{\"anioId\":" + anioId + ",\"gradoId\":" + gradoId + ",\"aulaId\":" + aulaId + ",\"nombre\":\"A\",\"capacidad\":35}", admin())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("supera la del aula")));
        seccionAId = crear("/api/v1/planificacion/secciones", "{\"anioId\":" + anioId + ",\"gradoId\":" + gradoId + ",\"aulaId\":" + aulaId + ",\"nombre\":\"A\",\"turno\":\"MANANA\",\"capacidad\":30}");
        seccionBId = crear("/api/v1/planificacion/secciones", "{\"anioId\":" + anioId + ",\"gradoId\":" + gradoId + ",\"nombre\":\"B\",\"turno\":\"MANANA\",\"capacidad\":30}");
        enviar("POST", "/api/v1/planificacion/secciones", "{\"anioId\":" + anioId + ",\"gradoId\":" + gradoId + ",\"nombre\":\"A\"}", admin())
                .andExpect(status().isConflict());

        cursoMatId = crear("/api/v1/planificacion/cursos", "{\"codigo\":\"MAT\",\"nombre\":\"Matemática\",\"area\":\"Matemática\"}");
        cursoComId = crear("/api/v1/planificacion/cursos", "{\"codigo\":\"COM\",\"nombre\":\"Comunicación\",\"area\":\"Letras\"}");
        crear("/api/v1/planificacion/malla", "{\"gradoId\":" + gradoId + ",\"cursoId\":" + cursoMatId + ",\"horasSemanales\":2}");
        crear("/api/v1/planificacion/malla", "{\"gradoId\":" + gradoId + ",\"cursoId\":" + cursoComId + ",\"horasSemanales\":1}");
        enviar("POST", "/api/v1/planificacion/malla", "{\"gradoId\":" + gradoId + ",\"cursoId\":" + cursoMatId + ",\"horasSemanales\":2}", admin())
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/planificacion/malla/grado/" + gradoId).with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalHoras").value(3)).andExpect(jsonPath("$.cursos.length()").value(2));
        mvc.perform(delete("/api/v1/planificacion/cursos/" + cursoMatId).with(admin())).andExpect(status().isConflict());
    }

    @Test
    @Order(6)
    void docentes_secretariaPuedeCrear_documentoUnico_yVinculoDeUsuario() throws Exception {
        String ana = "{\"numeroDocumento\":\"40123456\",\"nombres\":\"Ana\",\"apellidos\":\"Quispe\",\"email\":\"ana@colegio.pe\"}";
        String body = enviar("POST", "/api/v1/planificacion/docentes", ana, usuario(2L, "SECRETARIA"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        docenteId = ((Number) JsonPath.read(body, "$.id")).longValue();
        enviar("POST", "/api/v1/planificacion/docentes", ana, usuario(2L, "SECRETARIA")).andExpect(status().isConflict());
        docente2Id = crear("/api/v1/planificacion/docentes", "{\"numeroDocumento\":\"40999999\",\"nombres\":\"Luis\",\"apellidos\":\"Mamani\"}");

        enviar("PATCH", "/api/v1/planificacion/docentes/" + docenteId + "/usuario", "{\"usuarioId\":77}", admin()).andExpect(status().isOk());
        enviar("PATCH", "/api/v1/planificacion/docentes/" + docente2Id + "/usuario", "{\"usuarioId\":77}", admin()).andExpect(status().isConflict());
        enviar("PATCH", "/api/v1/planificacion/docentes/" + docente2Id + "/usuario", "{\"usuarioId\":78}", admin()).andExpect(status().isOk());

        // listados sin filtros: los parámetros nulos no deben romper la consulta en PostgreSQL
        mvc.perform(get("/api/v1/planificacion/docentes").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/v1/planificacion/secciones").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/v1/planificacion/docentes?q=quis").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.contenido[0].apellidos").value("Quispe"));
    }

    @Test
    @Order(7)
    void asignaciones_elCursoDebeEstarEnLaMalla_yUnaPorSeccionYCurso() throws Exception {
        long fueraDeMalla = crear("/api/v1/planificacion/cursos", "{\"codigo\":\"ART\",\"nombre\":\"Arte\"}");
        var sec = usuario(2L, "SECRETARIA");
        enviar("POST", "/api/v1/planificacion/asignaciones", "{\"seccionId\":" + seccionAId + ",\"cursoId\":" + fueraDeMalla + ",\"docenteId\":" + docenteId + "}", sec)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("no está en la malla")));
        String body = enviar("POST", "/api/v1/planificacion/asignaciones", "{\"seccionId\":" + seccionAId + ",\"cursoId\":" + cursoMatId + ",\"docenteId\":" + docenteId + "}", sec)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        asignacionId = ((Number) JsonPath.read(body, "$.id")).longValue();
        enviar("POST", "/api/v1/planificacion/asignaciones", "{\"seccionId\":" + seccionAId + ",\"cursoId\":" + cursoMatId + ",\"docenteId\":" + docente2Id + "}", sec)
                .andExpect(status().isConflict());
        body = enviar("POST", "/api/v1/planificacion/asignaciones", "{\"seccionId\":" + seccionBId + ",\"cursoId\":" + cursoMatId + ",\"docenteId\":" + docenteId + "}", sec)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        asignacionBId = ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    @Order(8)
    void bloques_sinSolapamientoPorTurno() throws Exception {
        bloque1Id = crear("/api/v1/planificacion/bloques", "{\"turno\":\"MANANA\",\"orden\":1,\"horaInicio\":\"08:00\",\"horaFin\":\"08:45\"}");
        bloque2Id = crear("/api/v1/planificacion/bloques", "{\"turno\":\"MANANA\",\"orden\":2,\"horaInicio\":\"08:45\",\"horaFin\":\"09:30\"}");
        recesoId = crear("/api/v1/planificacion/bloques", "{\"turno\":\"MANANA\",\"orden\":3,\"horaInicio\":\"09:30\",\"horaFin\":\"10:00\",\"esReceso\":true}");
        bloqueTardeId = crear("/api/v1/planificacion/bloques", "{\"turno\":\"TARDE\",\"orden\":1,\"horaInicio\":\"14:00\",\"horaFin\":\"14:45\"}");
        enviar("POST", "/api/v1/planificacion/bloques", "{\"turno\":\"MANANA\",\"orden\":4,\"horaInicio\":\"08:30\",\"horaFin\":\"09:00\"}", admin())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("solapa")));
        enviar("POST", "/api/v1/planificacion/bloques", "{\"turno\":\"MANANA\",\"orden\":1,\"horaInicio\":\"11:00\",\"horaFin\":\"11:45\"}", admin())
                .andExpect(status().isConflict());
    }

    @Test
    @Order(9)
    void horario_lasSeisReglasDevuelven409ConElMotivo() throws Exception {
        var sec = usuario(2L, "SECRETARIA");
        String ok = "{\"asignacionId\":" + asignacionId + ",\"bloqueId\":" + bloque1Id + ",\"diaSemana\":1}";
        String body = enviar("POST", "/api/v1/planificacion/horarios", ok, sec).andExpect(status().isCreated())
                .andExpect(jsonPath("$.aulaNombre").value("Aula 101")).andReturn().getResponse().getContentAsString();
        horarioId = ((Number) JsonPath.read(body, "$.id")).longValue();

        // (1) el mismo docente, en otra sección, mismo día y bloque
        enviar("POST", "/api/v1/planificacion/horarios", "{\"asignacionId\":" + asignacionBId + ",\"bloqueId\":" + bloque1Id + ",\"diaSemana\":1}", sec)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("El docente Quispe, Ana ya tiene clase el lunes en el bloque 1")));
        // (2) aula ocupada
        enviar("POST", "/api/v1/planificacion/horarios", "{\"asignacionId\":" + asignacionBId + ",\"bloqueId\":" + bloque1Id + ",\"aulaId\":" + aulaId + ",\"diaSemana\":2}", sec)
                .andExpect(status().isCreated()); // martes: libre
        enviar("PUT", "/api/v1/planificacion/horarios/" + horarioId, "{\"asignacionId\":" + asignacionId + ",\"bloqueId\":" + bloque1Id + ",\"aulaId\":" + aulaId + ",\"diaSemana\":2}", sec)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("tiene clase el martes")));
        // (4) bloque de otro turno
        enviar("POST", "/api/v1/planificacion/horarios", "{\"asignacionId\":" + asignacionId + ",\"bloqueId\":" + bloqueTardeId + ",\"diaSemana\":3}", sec)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("turno TARDE")));
        // (5) receso
        enviar("POST", "/api/v1/planificacion/horarios", "{\"asignacionId\":" + asignacionId + ",\"bloqueId\":" + recesoId + ",\"diaSemana\":3}", sec)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("receso")));
        // (6) horas semanales: Matemática tiene 2; se programa la 2.ª y la 3.ª falla
        enviar("POST", "/api/v1/planificacion/horarios", "{\"asignacionId\":" + asignacionId + ",\"bloqueId\":" + bloque1Id + ",\"diaSemana\":3}", sec)
                .andExpect(status().isCreated());
        enviar("POST", "/api/v1/planificacion/horarios", "{\"asignacionId\":" + asignacionId + ",\"bloqueId\":" + bloque1Id + ",\"diaSemana\":4}", sec)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("2 horas semanales")));
        // (3) sección con otra clase: otro curso de la misma sección en el mismo lunes bloque 1
        long comAsig = ((Number) JsonPath.read(enviar("POST", "/api/v1/planificacion/asignaciones",
                "{\"seccionId\":" + seccionAId + ",\"cursoId\":" + cursoComId + ",\"docenteId\":" + docente2Id + "}", sec)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        // con otra aula libre y otro docente libre, el único choque es el de la propia sección
        long aula2Id = crear("/api/v1/planificacion/aulas", "{\"codigo\":\"A-102\",\"nombre\":\"Aula 102\",\"capacidad\":30}");
        enviar("POST", "/api/v1/planificacion/horarios", "{\"asignacionId\":" + comAsig + ",\"bloqueId\":" + bloque1Id + ",\"aulaId\":" + aula2Id + ",\"diaSemana\":1}", sec)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("La sección 1° de Primaria A ya tiene otra clase")));
        // y con el aula de la sección ocupada, el motivo es el aula (regla 2 antes que la 3)
        enviar("POST", "/api/v1/planificacion/horarios", "{\"asignacionId\":" + comAsig + ",\"bloqueId\":" + bloque1Id + ",\"diaSemana\":1}", sec)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("El aula A-101 ya está ocupada")));
        enviar("POST", "/api/v1/planificacion/horarios", "{\"asignacionId\":" + comAsig + ",\"bloqueId\":" + bloque2Id + ",\"diaSemana\":1}", sec)
                .andExpect(status().isCreated());
    }

    @Test
    @Order(10)
    void validarYPublicarElHorario() throws Exception {
        var sec = usuario(2L, "SECRETARIA");
        // Sección B: solo tiene 1 de las 2 horas de Matemática y nada de Comunicación
        mvc.perform(get("/api/v1/planificacion/horarios/validar/" + seccionBId).with(sec))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valido").value(false))
                .andExpect(jsonPath("$.choques").isEmpty())
                .andExpect(jsonPath("$.cursosIncompletos[*].estado", hasItem("FALTANTE")))
                .andExpect(jsonPath("$.cursosIncompletos[*].estado", hasItem("SIN_DOCENTE")));
        // Sección A: completa (Matemática 2/2, Comunicación 1/1)
        mvc.perform(get("/api/v1/planificacion/horarios/validar/" + seccionAId).with(sec))
                .andExpect(jsonPath("$.valido").value(true));
        mvc.perform(get("/api/v1/planificacion/horarios/seccion/" + seccionAId).with(sec))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clases.length()").value(3))
                .andExpect(jsonPath("$.bloques.length()").value(3));
        mvc.perform(get("/api/v1/planificacion/horarios/docente/" + docenteId).with(sec))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clases.length()").value(3));

        reset(kafkaTemplate);
        when(kafkaTemplate.send(any(String.class), any(String.class), any(String.class))).thenReturn(CompletableFuture.completedFuture(null));
        mvc.perform(post("/api/v1/planificacion/horarios/publicar/seccion/" + seccionAId).with(usuario(55L, "SECRETARIA")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.publicadoPor").value(55));
        mvc.perform(post("/api/v1/planificacion/horarios/publicar/seccion/" + seccionAId).with(sec))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(2));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, timeout(2000).times(2)).send(eq("horario.publicado"), eq(String.valueOf(seccionAId)), json.capture());
        assertThat(json.getAllValues().get(0))
                .contains("\"versionHorario\":1", "\"seccionNombre\":\"1° de Primaria A\"", "\"docenteUsuarioIds\":[77,78]")
                .contains("\"eventId\"", "\"version\":1");
        mvc.perform(get("/api/v1/planificacion/horarios/publicar/seccion/" + seccionAId).with(sec))
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].version").value(2));
        // Sección vacía no se publica
        long vacia = crear("/api/v1/planificacion/secciones", "{\"anioId\":" + anioId + ",\"gradoId\":" + gradoId + ",\"nombre\":\"C\"}");
        mvc.perform(post("/api/v1/planificacion/horarios/publicar/seccion/" + vacia).with(sec)).andExpect(status().isConflict());
    }

    @Test
    @Order(11)
    void feriados_yConsultaDeDiaHabil() throws Exception {
        crear("/api/v1/planificacion/feriados", "{\"anioId\":" + anioId + ",\"fecha\":\"2026-07-28\",\"descripcion\":\"Fiestas Patrias\"}");
        enviar("POST", "/api/v1/planificacion/feriados", "{\"anioId\":" + anioId + ",\"fecha\":\"2026-07-28\",\"descripcion\":\"Repetido\"}", admin())
                .andExpect(status().isConflict());
        enviar("POST", "/api/v1/planificacion/feriados", "{\"anioId\":" + anioId + ",\"fecha\":\"2025-07-28\",\"descripcion\":\"Fuera\"}", admin())
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/planificacion/feriados/es-habil?fecha=2026-07-28").with(admin()))
                .andExpect(jsonPath("$.habil").value(false)).andExpect(jsonPath("$.motivo").value("Fiestas Patrias"));
        mvc.perform(get("/api/v1/planificacion/feriados/es-habil?fecha=2026-07-25").with(admin()))
                .andExpect(jsonPath("$.habil").value(false)).andExpect(jsonPath("$.motivo").value("Fin de semana"));
        mvc.perform(get("/api/v1/planificacion/feriados/es-habil?fecha=2026-07-27").with(admin()))
                .andExpect(jsonPath("$.habil").value(true));
    }

    @Test
    @Order(12)
    void endpointsInternos_sinTokenParaOtrosServicios() throws Exception {
        // el propio docente se identifica por su JWT; un usuario sin vínculo recibe 404
        mvc.perform(get("/api/v1/planificacion/docentes/mi").with(usuario(77L, "DOCENTE"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.docenteId").value(docenteId));
        mvc.perform(get("/api/v1/planificacion/docentes/mi").with(usuario(999L, "DOCENTE"))).andExpect(status().isNotFound());
        mvc.perform(interno("/internal/docentes/usuario/77")).andExpect(status().isOk())
                .andExpect(jsonPath("$.docenteId").value(docenteId)).andExpect(jsonPath("$.nombre").value("Quispe, Ana"));
        mvc.perform(interno("/internal/docentes/usuario/77/asignaciones?anioId=" + anioId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[*].seccionId", hasItem((int) seccionAId)));
        mvc.perform(interno("/internal/asignaciones/validar?docenteId=" + docenteId + "&seccionId=" + seccionAId + "&cursoId=" + cursoMatId))
                .andExpect(status().isOk()).andExpect(content().string("true"));
        mvc.perform(interno("/internal/asignaciones/validar?docenteId=" + docente2Id + "&seccionId=" + seccionAId + "&cursoId=" + cursoMatId))
                .andExpect(content().string("false"));
        mvc.perform(interno("/internal/secciones/" + seccionAId)).andExpect(jsonPath("$.nombre").value("A"))
                .andExpect(jsonPath("$.gradoId").value(gradoId)).andExpect(jsonPath("$.capacidad").value(30)).andExpect(jsonPath("$.turno").value("MANANA"));
        mvc.perform(interno("/internal/secciones?anioId=" + anioId + "&gradoId=" + gradoId)).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(interno("/internal/anios/activo")).andExpect(jsonPath("$.nombre").value("2026"));
        mvc.perform(interno("/internal/anios/" + anioId)).andExpect(jsonPath("$.nombre").value("2026")).andExpect(jsonPath("$.fechaInicio").exists());
        mvc.perform(interno("/internal/anios/999999")).andExpect(status().isNotFound());
        mvc.perform(interno("/internal/periodos?anioId=" + anioId)).andExpect(jsonPath("$[0].estado").value("ACTIVO"));
        mvc.perform(interno("/internal/cursos?ids=" + cursoMatId + "," + cursoComId)).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(interno("/internal/grados/" + gradoId)).andExpect(jsonPath("$.nivelNombre").value("Primaria"));
        mvc.perform(interno("/internal/feriados/es-habil?fecha=2026-07-28")).andExpect(content().string("false"));
        mvc.perform(interno("/internal/docentes/usuario/9999")).andExpect(status().isNotFound());
    }

    @Test
    @Order(13)
    void anio_cerradoEsDeSoloLectura() throws Exception {
        mvc.perform(patch("/api/v1/planificacion/anios/" + anioId + "/cerrar").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CERRADO"));
        enviar("POST", "/api/v1/planificacion/horarios", "{\"asignacionId\":" + asignacionBId + ",\"bloqueId\":" + bloque2Id + ",\"diaSemana\":5}", usuario(2L, "SECRETARIA"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("CERRADO")));
        enviar("POST", "/api/v1/planificacion/secciones", "{\"anioId\":" + anioId + ",\"gradoId\":" + gradoId + ",\"nombre\":\"Z\"}", admin())
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/planificacion/horarios/" + horarioId).with(admin())).andExpect(status().isConflict());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder interno(String url) {
        return get(url).header("X-Service-Token", "test-token");
    }

    @Test
    void internoSinTokenOConTokenErroneoDa401() throws Exception {
        mvc.perform(get("/internal/anios/activo")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/anios/activo").header("X-Service-Token", "otro")).andExpect(status().isUnauthorized());
    }
}
