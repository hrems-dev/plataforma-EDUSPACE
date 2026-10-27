package pe.edu.eduspace.alumnos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.validation.Validation;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.edu.eduspace.alumnos.dto.AlumnoCreateRequest;
import pe.edu.eduspace.alumnos.dto.AlumnoResponse;
import pe.edu.eduspace.alumnos.dto.ImportacionResponse;
import pe.edu.eduspace.alumnos.exception.DatosInvalidosException;
import pe.edu.eduspace.alumnos.exception.DuplicateResourceException;
import pe.edu.eduspace.alumnos.service.impl.ImportacionAlumnosServiceImpl;

class ImportacionAlumnosServiceTest {

    static final String CABECERA = "tipoDocumento;numeroDocumento;nombres;apellidoPaterno;apellidoMaterno;fechaNacimiento;sexo;"
            + "apoderadoTipoDocumento;apoderadoNumeroDocumento;apoderadoNombres;apoderadoApellidos;apoderadoParentesco\n";

    AlumnoService alumnos = mock(AlumnoService.class);
    ImportacionAlumnosService service;

    @BeforeEach
    void preparar() {
        service = new ImportacionAlumnosServiceImpl(alumnos, Validation.buildDefaultValidatorFactory().getValidator());
    }

    private ImportacionResponse importar(String csv) {
        return service.importar(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    private AlumnoResponse creado(long id, String codigo) {
        return new AlumnoResponse(id, codigo, null, "DNI", null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    @Test
    void cadaFilaVaPorSeparado_unaMalaNoTumbaLasDemas() {
        when(alumnos.crear(any(AlumnoCreateRequest.class)))
                .thenReturn(creado(1, "AL2026-00001"))
                .thenThrow(new DuplicateResourceException("Ya existe un alumno con DNI 70000002"));
        String csv = CABECERA
                + "DNI;70000001;Ana;Ruiz;Paz;2016-03-01;f;DNI;40000001;Rosa;Ruiz;madre\n"
                + "DNI;70000002;Beto;Soto;;2015-05-05;M;DNI;40000002;Juan;Soto;PADRE\n"
                + "DNI;70000003;Cleo;Vega;;2015-13-45;M;DNI;40000003;Eva;Vega;MADRE\n"
                + "DNI;70000004;Dino;Rey;;2015-01-01;M;DNI;40000004;Pia;Rey;AMIGA\n";

        ImportacionResponse r = importar(csv);

        assertThat(r.total()).isEqualTo(4);
        assertThat(r.creados()).isEqualTo(1);
        assertThat(r.fallidos()).isEqualTo(3);
        assertThat(r.filas().get(0).codigo()).isEqualTo("AL2026-00001");
        assertThat(r.filas().get(1).error()).contains("Ya existe");
        assertThat(r.filas().get(2).error()).contains("AAAA-MM-DD");
        assertThat(r.filas().get(3).error()).contains("apoderadoParentesco");
        assertThat(r.filas().get(3).linea()).isEqualTo(5);
        verify(alumnos, times(2)).crear(any());
    }

    @Test
    void elApoderadoUnicoQuedaComoPrincipalYResponsableDePago() {
        when(alumnos.crear(any(AlumnoCreateRequest.class))).thenReturn(creado(1, "AL2026-00001"));
        importar(CABECERA + "DNI;70000001;Ana;Ruiz;;2016-03-01;F;DNI;40000001;Rosa;Ruiz;MADRE\n");
        var captor = org.mockito.ArgumentCaptor.forClass(AlumnoCreateRequest.class);
        verify(alumnos).crear(captor.capture());
        var v = captor.getValue().apoderados().get(0);
        assertThat(v.esPrincipal()).isTrue();
        assertThat(v.esResponsablePago()).isTrue();
        assertThat(captor.getValue().sexo()).isEqualTo("F");
    }

    @Test
    void faltandoUnaColumnaObligatoriaSeRechazaTodoElArchivo() {
        assertThatThrownBy(() -> importar("numeroDocumento;nombres\n70000001;Ana\n"))
                .isInstanceOf(DatosInvalidosException.class).hasMessageContaining("Faltan columnas");
    }

    @Test
    void archivoVacioSeRechaza() {
        assertThatThrownBy(() -> importar("")).isInstanceOf(DatosInvalidosException.class);
    }
}
