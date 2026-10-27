package pe.edu.eduspace.planificacion.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.eduspace.planificacion.dto.SeccionRequest;
import pe.edu.eduspace.planificacion.entity.*;
import pe.edu.eduspace.planificacion.exception.BusinessRuleException;
import pe.edu.eduspace.planificacion.mapper.PlanificacionMapper;
import pe.edu.eduspace.planificacion.repository.*;
import pe.edu.eduspace.planificacion.service.impl.SeccionServiceImpl;

@ExtendWith(MockitoExtension.class)
class SeccionServiceTest {

    @Mock SeccionRepository repository;
    @Mock AnioAcademicoRepository anioRepository;
    @Mock GradoRepository gradoRepository;
    @Mock AulaRepository aulaRepository;
    @Mock DocenteRepository docenteRepository;
    @Mock AsignacionRepository asignacionRepository;
    @Mock HorarioRepository horarioRepository;
    @Mock PlanificacionMapper mapper;
    SeccionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SeccionServiceImpl(repository, anioRepository, gradoRepository, aulaRepository,
                docenteRepository, asignacionRepository, horarioRepository, mapper);
        AnioAcademico anio = new AnioAcademico();
        anio.setId(1L);
        anio.setNombre("2026");
        anio.setEstado(EstadoPeriodo.ACTIVO);
        anio.setFechaInicio(LocalDate.of(2026, 3, 1));
        anio.setFechaFin(LocalDate.of(2026, 12, 20));
        Grado grado = new Grado();
        grado.setId(4L);
        grado.setNombre("1° de Primaria");
        Aula aula = new Aula();
        aula.setId(7L);
        aula.setCodigo("A-101");
        aula.setCapacidad((short) 30);
        aula.setActivo(true);
        lenient().when(anioRepository.findById(1L)).thenReturn(Optional.of(anio));
        lenient().when(gradoRepository.findById(4L)).thenReturn(Optional.of(grado));
        lenient().when(aulaRepository.findById(7L)).thenReturn(Optional.of(aula));
    }

    @Test
    void laCapacidadDeLaSeccionNoPuedeSuperarLaDelAula() {
        SeccionRequest request = new SeccionRequest(1L, 4L, 7L, "A", Turno.MANANA, (short) 31, null, true);
        assertThatThrownBy(() -> service.crear(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("supera la del aula");
        verify(repository, never()).save(any());
    }

    @Test
    void noPermiteSeccionesRepetidasEnElMismoAnioYGrado() {
        org.mockito.Mockito.when(repository.existsByAnioIdAndGradoIdAndNombre(1L, 4L, "A")).thenReturn(true);
        SeccionRequest request = new SeccionRequest(1L, 4L, 7L, "A", Turno.MANANA, (short) 25, null, true);
        assertThatThrownBy(() -> service.crear(request))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Ya existe la sección");
    }
}
