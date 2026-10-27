package pe.edu.eduspace.planificacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.eduspace.planificacion.dto.HorarioRequest;
import pe.edu.eduspace.planificacion.entity.*;
import pe.edu.eduspace.planificacion.exception.BusinessRuleException;
import pe.edu.eduspace.planificacion.mapper.PlanificacionMapper;
import pe.edu.eduspace.planificacion.repository.*;
import pe.edu.eduspace.planificacion.service.impl.HorarioServiceImpl;

/** Cada una de las seis reglas del horario debe devolver 409 con el motivo exacto. */
@ExtendWith(MockitoExtension.class)
class HorarioServiceTest {

    @Mock HorarioRepository repository;
    @Mock AsignacionRepository asignacionRepository;
    @Mock BloqueHorarioRepository bloqueRepository;
    @Mock AulaRepository aulaRepository;
    @Mock SeccionRepository seccionRepository;
    @Mock DocenteRepository docenteRepository;
    @Mock GradoCursoRepository mallaRepository;
    @Mock PlanificacionMapper mapper;
    HorarioServiceImpl service;

    Asignacion asignacion;
    Seccion seccion;
    BloqueHorario bloque;
    Aula aula;
    GradoCurso malla;
    final HorarioRequest request = new HorarioRequest(10L, 20L, 7L, (short) 1);

    @BeforeEach
    void setUp() {
        service = new HorarioServiceImpl(repository, asignacionRepository, bloqueRepository, aulaRepository,
                seccionRepository, docenteRepository, mallaRepository, mapper);
        AnioAcademico anio = new AnioAcademico();
        anio.setId(1L);
        anio.setNombre("2026");
        anio.setEstado(EstadoPeriodo.ACTIVO);
        anio.setFechaInicio(LocalDate.of(2026, 3, 1));
        anio.setFechaFin(LocalDate.of(2026, 12, 20));
        Grado grado = new Grado();
        grado.setId(4L);
        grado.setNombre("1° de Primaria");
        seccion = new Seccion();
        seccion.setId(5L);
        seccion.setNombre("A");
        seccion.setAnio(anio);
        seccion.setGrado(grado);
        seccion.setTurno(Turno.MANANA);
        Docente docente = new Docente();
        docente.setId(3L);
        docente.setNombres("Ana");
        docente.setApellidos("Quispe");
        Curso curso = new Curso();
        curso.setId(8L);
        curso.setNombre("Matemática");
        asignacion = new Asignacion();
        asignacion.setId(10L);
        asignacion.setSeccion(seccion);
        asignacion.setDocente(docente);
        asignacion.setCurso(curso);
        bloque = new BloqueHorario();
        bloque.setId(20L);
        bloque.setTurno(Turno.MANANA);
        bloque.setOrden((short) 1);
        bloque.setHoraInicio(LocalTime.of(8, 0));
        bloque.setHoraFin(LocalTime.of(8, 45));
        bloque.setEsReceso(false);
        aula = new Aula();
        aula.setId(7L);
        aula.setCodigo("A-101");
        aula.setActivo(true);
        malla = new GradoCurso();
        malla.setHorasSemanales((short) 2);

        lenient().when(asignacionRepository.findById(10L)).thenReturn(Optional.of(asignacion));
        lenient().when(bloqueRepository.findById(20L)).thenReturn(Optional.of(bloque));
        lenient().when(aulaRepository.findById(7L)).thenReturn(Optional.of(aula));
        lenient().when(mallaRepository.findByGradoIdAndCursoId(4L, 8L)).thenReturn(Optional.of(malla));
        lenient().when(repository.countByAsignacionExcluyendo(eq(10L), any())).thenReturn(0L);
    }

    @Test
    void regla1_docenteOcupado() {
        when(repository.existsChoqueDocente(3L, (short) 1, 20L, null)).thenReturn(true);
        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("El docente Quispe, Ana ya tiene clase el lunes en el bloque 1 (08:00-08:45)");
        verify(repository, never()).save(any());
    }

    @Test
    void regla2_aulaOcupada() {
        when(repository.existsChoqueAula(7L, (short) 1, 20L, null)).thenReturn(true);
        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("El aula A-101 ya está ocupada el lunes en el bloque 1");
    }

    @Test
    void regla3_seccionConOtraClase() {
        when(repository.existsChoqueSeccion(5L, (short) 1, 20L, null)).thenReturn(true);
        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("La sección 1° de Primaria A ya tiene otra clase el lunes en el bloque 1");
    }

    @Test
    void regla4_bloqueDeOtroTurno() {
        bloque.setTurno(Turno.TARDE);
        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("es del turno TARDE")
                .hasMessageContaining("es del turno MANANA");
    }

    @Test
    void regla5_bloqueDeReceso() {
        bloque.setEsReceso(true);
        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("es un receso");
    }

    @Test
    void regla6_excedeLasHorasSemanalesDeLaMalla() {
        when(repository.countByAsignacionExcluyendo(10L, null)).thenReturn(2L);
        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya tiene sus 2 horas semanales programadas");
    }

    @Test
    void sinChoquesGuardaLaClase() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.crear(request);
        var captor = org.mockito.ArgumentCaptor.forClass(Horario.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAula()).isSameAs(aula);
        assertThat(captor.getValue().getDiaSemana()).isEqualTo((short) 1);
    }

    @Test
    void unAnioCerradoNoAdmiteCambiosDeHorario() {
        seccion.getAnio().setEstado(EstadoPeriodo.CERRADO);
        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CERRADO");
    }
}
