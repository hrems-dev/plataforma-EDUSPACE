package pe.edu.eduspace.alumnos.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.eduspace.alumnos.storage.AlmacenamientoArchivos;

/** Recorrido completo de la API con PostgreSQL real, seguridad JWT, almacenamiento simulado y captura de eventos. */
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AlumnosApiIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired MockMvc mvc;
    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;
    @MockitoBean AlmacenamientoArchivos almacenamiento;

    static long alumno1, alumno2, padreId, madreId, abuelaVinculo, padreVinculo, madreVinculo, docDniId;
    static final String ANIO = String.valueOf(LocalDate.now().getYear());

    JwtRequestPostProcessor usuario(long usuarioId, String rol) {
        return jwt().jwt(j -> j.claim("usuarioId", usuarioId).claim("realm_access", Map.of("roles", List.of(rol))))
                .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    JwtRequestPostProcessor secretaria() {
        return usuario(2L, "SECRETARIA");
    }

    ResultActions enviar(String metodo, String ruta, String cuerpo, JwtRequestPostProcessor quien) throws Exception {
        var req = switch (metodo) {
            case "POST" -> post(ruta);
            case "PUT" -> put(ruta);
            case "PATCH" -> patch(ruta);
            default -> throw new IllegalArgumentException(metodo);
        };
        return mvc.perform(req.with(quien).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    /** Primer valor numérico de una ruta JsonPath con filtro (que siempre devuelve una lista). */
    static long primerId(String json, String ruta) {
        List<?> valores = JsonPath.read(json, ruta);
        return ((Number) valores.get(0)).longValue();
    }

    static String alumnoJson(String doc, String nombres, String apellidoP, String apoderados) {
        return "{\"numeroDocumento\":\"" + doc + "\",\"nombres\":\"" + nombres + "\",\"apellidoPaterno\":\"" + apellidoP
                + "\",\"apellidoMaterno\":\"Pérez\",\"fechaNacimiento\":\"2016-04-12\",\"sexo\":\"M\",\"apoderados\":" + apoderados + "}";
    }

    static String apoderadoJson(String doc, String nombres, String parentesco, boolean principal, boolean pago) {
        return "{\"apoderado\":{\"numeroDocumento\":\"" + doc + "\",\"nombres\":\"" + nombres + "\",\"apellidos\":\"Ramos Díaz\","
                + "\"email\":\"" + nombres.toLowerCase() + "@mail.pe\",\"telefono\":\"9" + doc.substring(0, 8) + "\"},"
                + "\"parentesco\":\"" + parentesco + "\",\"esPrincipal\":" + principal + ",\"esResponsablePago\":" + pago + "}";
    }

    @Test
    @Order(1)
    void alta_generaCodigo_vinculaApoderados_yPublicaEventoSinDatosSensibles() throws Exception {
        reset(kafkaTemplate);
        when(kafkaTemplate.send(any(String.class), any(String.class), any(String.class))).thenReturn(CompletableFuture.completedFuture(null));
        String apoderados = "[" + apoderadoJson("40111111", "Carlos", "PADRE", true, true) + ","
                + apoderadoJson("40222222", "María", "MADRE", false, false) + "]";

        String body = enviar("POST", "/api/v1/alumnos", alumnoJson("70123456", "Luis", "Ramos", apoderados), secretaria())
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.codigo").value("AL" + ANIO + "-00001"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.nombreCompleto").value("Ramos Pérez, Luis"))
                .andExpect(jsonPath("$.apoderados.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        alumno1 = ((Number) JsonPath.read(body, "$.id")).longValue();
        padreId = primerId(body, "$.apoderados[?(@.parentesco=='PADRE')].apoderado.id");
        madreId = primerId(body, "$.apoderados[?(@.parentesco=='MADRE')].apoderado.id");

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, timeout(2000)).send(eq("alumno.registrado"), eq(String.valueOf(alumno1)), json.capture());
        assertThat(json.getValue())
                .contains("\"alumnoId\":" + alumno1, "\"codigo\":\"AL" + ANIO + "-00001\"", "\"fechaNacimiento\":\"2016-04-12\"",
                        "\"apoderadoIds\":[", "\"eventId\":\"", "\"eventType\":\"alumno.registrado\"")
                .doesNotContain("70123456", "40111111", "40222222", "alergias", "documento");
    }

    @Test
    @Order(2)
    void alta_rechazaDuplicados_formatosYReglasDeApoderados() throws Exception {
        String uno = "[" + apoderadoJson("40111111", "Carlos", "PADRE", true, true) + "]";
        enviar("POST", "/api/v1/alumnos", alumnoJson("70123456", "Otro", "Rojas", uno), secretaria())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("DUPLICADO"));
        enviar("POST", "/api/v1/alumnos", alumnoJson("123", "Otro", "Rojas", uno), secretaria())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.mensaje", containsString("DNI")));
        String sinPrincipal = "[" + apoderadoJson("40111111", "Carlos", "PADRE", false, false) + "," + apoderadoJson("40222222", "María", "MADRE", false, false) + "]";
        enviar("POST", "/api/v1/alumnos", alumnoJson("70999991", "Otro", "Rojas", sinPrincipal), secretaria())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("principal")));
        String dosPagos = "[" + apoderadoJson("40111111", "Carlos", "PADRE", true, true) + "," + apoderadoJson("40222222", "María", "MADRE", false, true) + "]";
        enviar("POST", "/api/v1/alumnos", alumnoJson("70999992", "Otro", "Rojas", dosPagos), secretaria())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("responsable de pago")));
        enviar("POST", "/api/v1/alumnos", alumnoJson("70999993", "Otro", "Rojas", "[]"), secretaria())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
        enviar("POST", "/api/v1/alumnos", "{\"numeroDocumento\":\"70999994\",\"nombres\":\"X\",\"apellidoPaterno\":\"Y\",\"fechaNacimiento\":\"2999-01-01\",\"sexo\":\"Z\",\"apoderados\":" + uno + "}", secretaria())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detalles", hasItems(containsString("fechaNacimiento"), containsString("sexo"))));
        // nada de lo rechazado dejó datos a medias
        mvc.perform(get("/api/v1/alumnos").with(secretaria())).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @Order(3)
    void unApoderadoConVariosHijos_seReutilizaPorDocumento() throws Exception {
        String apoderados = "[" + apoderadoJson("40111111", "Carlos", "PADRE", true, true) + "]";
        String body = enviar("POST", "/api/v1/alumnos", alumnoJson("70123457", "Ana", "Ramos", apoderados), secretaria())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.codigo").value("AL" + ANIO + "-00002"))
                .andExpect(jsonPath("$.apoderados[0].apoderado.id").value(padreId))
                .andReturn().getResponse().getContentAsString();
        alumno2 = ((Number) JsonPath.read(body, "$.id")).longValue();
        mvc.perform(get("/api/v1/apoderados/" + padreId + "/hijos").with(secretaria()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/v1/alumnos?q=ramos&estado=ACTIVO").with(secretaria()))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/v1/alumnos?q=ana").with(usuario(9L, "DOCENTE")))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.contenido[0].codigo").value("AL" + ANIO + "-00002"));
    }

    @Test
    @Order(4)
    void vinculos_principalYResponsableDePagoSiempreSonUnicos() throws Exception {
        String lista = mvc.perform(get("/api/v1/alumnos/" + alumno1 + "/apoderados").with(secretaria()))
                .andReturn().getResponse().getContentAsString();
        padreVinculo = primerId(lista, "$[?(@.parentesco=='PADRE')].id");
        madreVinculo = primerId(lista, "$[?(@.parentesco=='MADRE')].id");

        // la abuela entra como principal: el padre deja de serlo
        String body = enviar("POST", "/api/v1/alumnos/" + alumno1 + "/apoderados",
                apoderadoJson("40333333", "Rosa", "ABUELA", true, false), secretaria()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        abuelaVinculo = ((Number) JsonPath.read(body, "$.id")).longValue();
        mvc.perform(get("/api/v1/alumnos/" + alumno1 + "/apoderados").with(secretaria()))
                .andExpect(jsonPath("$[?(@.esPrincipal==true)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.esPrincipal==true)].parentesco", hasItem("ABUELA")));
        // no se puede quitar ni degradar al principal
        mvc.perform(delete("/api/v1/alumnos/" + alumno1 + "/apoderados/" + abuelaVinculo).with(secretaria()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("principal")));
        enviar("PUT", "/api/v1/alumnos/" + alumno1 + "/apoderados/" + abuelaVinculo, "{\"esPrincipal\":false}", secretaria())
                .andExpect(status().isConflict());
        // la madre pasa a ser responsable de pago: el padre deja de serlo
        enviar("PUT", "/api/v1/alumnos/" + alumno1 + "/apoderados/" + madreVinculo, "{\"esResponsablePago\":true}", secretaria())
                .andExpect(status().isOk()).andExpect(jsonPath("$.esResponsablePago").value(true));
        mvc.perform(get("/api/v1/alumnos/" + alumno1 + "/apoderados").with(secretaria()))
                .andExpect(jsonPath("$[?(@.esResponsablePago==true)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.esResponsablePago==true)].parentesco", hasItem("MADRE")));
        // el mismo apoderado no se vincula dos veces; quitar a quien no es principal sí se puede
        enviar("POST", "/api/v1/alumnos/" + alumno1 + "/apoderados", "{\"apoderadoId\":" + padreId + ",\"parentesco\":\"PADRE\"}", secretaria())
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/alumnos/" + alumno1 + "/apoderados/" + padreVinculo).with(secretaria()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(5)
    void estado_exigeMotivo_dejaHistorialYPublicaEvento() throws Exception {
        reset(kafkaTemplate);
        when(kafkaTemplate.send(any(String.class), any(String.class), any(String.class))).thenReturn(CompletableFuture.completedFuture(null));
        enviar("PATCH", "/api/v1/alumnos/" + alumno2 + "/estado", "{\"estado\":\"RETIRADO\"}", secretaria())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detalles", hasItem(containsString("motivo"))));
        enviar("PATCH", "/api/v1/alumnos/" + alumno2 + "/estado", "{\"estado\":\"ACTIVO\",\"motivo\":\"igual\"}", secretaria())
                .andExpect(status().isConflict());
        enviar("PATCH", "/api/v1/alumnos/" + alumno2 + "/estado", "{\"estado\":\"RETIRADO\",\"motivo\":\"Traslado de ciudad\"}", usuario(55L, "SECRETARIA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RETIRADO"));
        mvc.perform(get("/api/v1/alumnos/" + alumno2 + "/estado/historial").with(secretaria()))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].estadoAnterior").value("ACTIVO"))
                .andExpect(jsonPath("$[0].motivo").value("Traslado de ciudad")).andExpect(jsonPath("$[0].usuarioId").value(55));
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, timeout(2000)).send(eq("alumno.estado-cambiado"), eq(String.valueOf(alumno2)), json.capture());
        assertThat(json.getValue()).contains("\"estadoNuevo\":\"RETIRADO\"", "\"motivo\":\"Traslado de ciudad\"");
        // un alumno no se borra: sigue existiendo, solo cambió de estado
        mvc.perform(get("/api/v1/alumnos/" + alumno2).with(secretaria())).andExpect(jsonPath("$.estado").value("RETIRADO"));
        mvc.perform(get("/api/v1/alumnos?estado=ACTIVO").with(secretaria())).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @Order(6)
    void documentos_subidaConAlmacenamientoSimulado_yExpedienteCompleto() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("archivo", "dni.pdf", "application/pdf", new byte[]{1, 2, 3, 4});
        String body = mvc.perform(multipart("/api/v1/alumnos/" + alumno1 + "/documentos").file(pdf).param("tipo", "DNI").with(secretaria()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.verificado").value(false))
                .andExpect(jsonPath("$.tamanoBytes").value(4)).andReturn().getResponse().getContentAsString();
        docDniId = ((Number) JsonPath.read(body, "$.id")).longValue();
        ArgumentCaptor<String> clave = ArgumentCaptor.forClass(String.class);
        verify(almacenamiento).guardar(clave.capture(), any(), eq(4L), eq("application/pdf"));
        assertThat(clave.getValue()).startsWith("alumnos/" + alumno1 + "/").endsWith("-dni.pdf");

        mvc.perform(multipart("/api/v1/alumnos/" + alumno1 + "/documentos")
                        .file(new MockMultipartFile("archivo", "virus.exe", "application/x-msdownload", new byte[]{1})).param("tipo", "OTRO").with(secretaria()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje", containsString("no permitido")));

        // expediente: faltan documentos requeridos mientras no estén todos verificados
        mvc.perform(interno("/internal/alumnos/" + alumno1 + "/expediente-completo")).andExpect(jsonPath("$.completo").value(false))
                .andExpect(jsonPath("$.faltantes", hasItems("PARTIDA_NACIMIENTO", "DNI", "FICHA_SIAGIE")));
        enviar("PATCH", "/api/v1/alumnos/" + alumno1 + "/documentos/" + docDniId + "/verificado", "{\"verificado\":true}", secretaria())
                .andExpect(jsonPath("$.verificado").value(true));
        for (String tipo : List.of("PARTIDA_NACIMIENTO", "FICHA_SIAGIE")) {
            String id = JsonPath.read(mvc.perform(multipart("/api/v1/alumnos/" + alumno1 + "/documentos")
                            .file(new MockMultipartFile("archivo", tipo + ".png", "image/png", new byte[]{9, 9})).param("tipo", tipo).with(secretaria()))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id").toString();
            enviar("PATCH", "/api/v1/alumnos/" + alumno1 + "/documentos/" + id + "/verificado", "{}", secretaria()).andExpect(status().isOk());
        }
        mvc.perform(interno("/internal/alumnos/" + alumno1 + "/expediente-completo")).andExpect(jsonPath("$.completo").value(true))
                .andExpect(jsonPath("$.faltantes").isEmpty());

        when(almacenamiento.urlDescarga(anyString(), any(Duration.class))).thenReturn("http://localhost:19000/firmada?X-Amz-Expires=600");
        mvc.perform(get("/api/v1/alumnos/" + alumno1 + "/documentos/" + docDniId + "/descarga").with(secretaria()))
                .andExpect(jsonPath("$.url").value(containsString("X-Amz-Expires"))).andExpect(jsonPath("$.expiraEnSegundos").value(600));
        mvc.perform(delete("/api/v1/alumnos/" + alumno1 + "/documentos/" + docDniId).with(secretaria())).andExpect(status().isNoContent());
        verify(almacenamiento).eliminar(clave.getValue());
        mvc.perform(get("/api/v1/alumnos/" + alumno1 + "/documentos").with(secretaria())).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @Order(7)
    void salud_soloPersonalAutorizado() throws Exception {
        mvc.perform(get("/api/v1/alumnos/" + alumno1 + "/salud").with(secretaria())).andExpect(status().isNotFound());
        enviar("PUT", "/api/v1/alumnos/" + alumno1 + "/salud", "{\"grupoSanguineo\":\"O+\",\"alergias\":\"Penicilina\",\"contactoEmergenciaNombre\":\"Rosa Ramos\",\"contactoEmergenciaTelefono\":\"987654321\"}", secretaria())
                .andExpect(status().isOk()).andExpect(jsonPath("$.alergias").value("Penicilina"));
        enviar("PUT", "/api/v1/alumnos/" + alumno1 + "/salud", "{\"grupoSanguineo\":\"A+\",\"alergias\":\"Ninguna\"}", secretaria())
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/alumnos/" + alumno1 + "/salud").with(usuario(9L, "DOCENTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.grupoSanguineo").value("A+"));
        enviar("PUT", "/api/v1/alumnos/" + alumno1 + "/salud", "{\"alergias\":\"x\"}", usuario(9L, "DOCENTE")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/alumnos/" + alumno1 + "/salud").with(usuario(300L, "APODERADO"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/alumnos/" + alumno1 + "/salud").with(usuario(301L, "ALUMNO"))).andExpect(status().isForbidden());
    }

    @Test
    @Order(8)
    void seguridad_alumnoYApoderadoSoloVenLoPropio() throws Exception {
        // cuentas: la madre (usuario 300) es apoderada de alumno1; el alumno1 tiene cuenta 301; otra apoderada 400 no tiene relación
        enviar("PATCH", "/api/v1/apoderados/" + madreId + "/usuario", "{\"usuarioId\":300}", secretaria()).andExpect(status().isOk());
        enviar("PATCH", "/api/v1/alumnos/" + alumno1 + "/usuario", "{\"usuarioId\":301}", secretaria()).andExpect(status().isOk());
        enviar("PATCH", "/api/v1/alumnos/" + alumno2 + "/usuario", "{\"usuarioId\":301}", secretaria()).andExpect(status().isConflict());

        mvc.perform(get("/api/v1/alumnos/" + alumno1).with(usuario(300L, "APODERADO"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/alumnos/" + alumno2).with(usuario(300L, "APODERADO"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/alumnos/" + alumno1).with(usuario(400L, "APODERADO"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/alumnos/" + alumno1).with(usuario(301L, "ALUMNO"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/alumnos/" + alumno2).with(usuario(301L, "ALUMNO"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/alumnos").with(usuario(300L, "APODERADO"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/alumnos/" + alumno1 + "/apoderados").with(usuario(300L, "APODERADO"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/apoderados").with(usuario(300L, "APODERADO"))).andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/alumnos/mios").with(usuario(300L, "APODERADO")))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].alumnoId").value(alumno1));
        mvc.perform(get("/api/v1/alumnos/mios").with(usuario(301L, "ALUMNO")))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].alumnoId").value(alumno1));
        mvc.perform(get("/api/v1/alumnos/mios").with(secretaria())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/apoderados/" + madreId).with(usuario(300L, "APODERADO"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/apoderados/" + padreId).with(usuario(300L, "APODERADO"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/apoderados/" + madreId + "/hijos").with(usuario(300L, "APODERADO")))
                .andExpect(jsonPath("$.length()").value(1));

        // escritura solo ADMIN/SECRETARIA; sin token 401
        enviar("POST", "/api/v1/alumnos", alumnoJson("70555555", "X", "Y", "[]"), usuario(9L, "DOCENTE")).andExpect(status().isForbidden());
        enviar("PUT", "/api/v1/alumnos/" + alumno1 + "/salud", "{}", usuario(300L, "APODERADO")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/alumnos/" + alumno1)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/alumnos/999999").with(secretaria())).andExpect(status().isNotFound());
    }

    @Test
    @Order(9)
    void endpointsInternos_paraNotificacionesMatriculaYPago() throws Exception {
        mvc.perform(interno("/internal/alumnos/" + alumno1)).andExpect(jsonPath("$.nombreCompleto").value("Ramos Pérez, Luis"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
        mvc.perform(interno("/internal/alumnos/" + alumno1 + "/contactos")).andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(301)).andExpect(jsonPath("$.apoderados.length()").value(2))
                .andExpect(jsonPath("$.apoderados[?(@.parentesco=='MADRE')].usuarioId").value(hasItem(300)))
                .andExpect(jsonPath("$.apoderados[?(@.parentesco=='MADRE')].email").value(hasItem("maría@mail.pe")))
                .andExpect(jsonPath("$.apoderados[?(@.parentesco=='MADRE')].esResponsablePago").value(hasItem(true)));
        mvc.perform(interno("/internal/alumnos/" + alumno1 + "/responsable-pago")).andExpect(jsonPath("$.usuarioId").value(300))
                .andExpect(jsonPath("$.nombre").value("Ramos Díaz, María"));
        mvc.perform(interno("/internal/alumnos/usuario/301")).andExpect(jsonPath("$.alumnoId").value(alumno1));
        mvc.perform(interno("/internal/alumnos/usuario/999")).andExpect(status().isNotFound());
        mvc.perform(interno("/internal/apoderados/usuario/300/alumnos")).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].parentesco").value("MADRE"));
        mvc.perform(interno("/internal/alumnos/lote?ids=" + alumno1 + "," + alumno2 + ",999999")).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(interno("/internal/alumnos/" + alumno2 + "/responsable-pago")).andExpect(status().isOk()); // el padre sigue siendo responsable de pago de alumno2
        mvc.perform(interno("/internal/alumnos/999999")).andExpect(status().isNotFound());
    }

    @Test
    @Order(11)
    void rutaInexistenteEs404YMetodoNoPermitidoEs405_noEs500() throws Exception {
        mvc.perform(get("/api/v1/no-existe").with(secretaria())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/alumnos/" + alumno1).with(secretaria())).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));   // un alumno nunca se borra
        mvc.perform(get("/api/v1/alumnos/abc").with(secretaria())).andExpect(status().isBadRequest());
    }

    @Test
    @Order(10)
    void actualizar_validaDocumentoUnico() throws Exception {
        enviar("PUT", "/api/v1/alumnos/" + alumno2, "{\"numeroDocumento\":\"70123456\",\"nombres\":\"Ana\",\"apellidoPaterno\":\"Ramos\",\"fechaNacimiento\":\"2017-01-01\",\"sexo\":\"F\"}", secretaria())
                .andExpect(status().isConflict());
        enviar("PUT", "/api/v1/alumnos/" + alumno2, "{\"numeroDocumento\":\"70123457\",\"nombres\":\"Ana María\",\"apellidoPaterno\":\"Ramos\",\"fechaNacimiento\":\"2017-01-01\",\"sexo\":\"F\",\"distrito\":\"Lima\"}", secretaria())
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombres").value("Ana María")).andExpect(jsonPath("$.codigo").value("AL" + ANIO + "-00002"));
    }

    @Test
    void elCajeroBuscaAlumnosPeroNoVeSuExpediente() throws Exception {
        mvc.perform(get("/api/v1/alumnos").param("q", "x").with(usuario(3L, "CAJERO"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/alumnos/1").with(usuario(3L, "CAJERO"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/alumnos/1/salud").with(usuario(3L, "CAJERO"))).andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder interno(String url) {
        return get(url).header("X-Service-Token", "test-token");
    }

    @Test
    void internoSinTokenOConTokenErroneoDa401() throws Exception {
        mvc.perform(get("/internal/alumnos/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/alumnos/1").header("X-Service-Token", "otro")).andExpect(status().isUnauthorized());
    }
}
