package pe.edu.eduspace.planificacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import pe.edu.eduspace.planificacion.dto.AnioRequest;
import pe.edu.eduspace.planificacion.entity.AnioAcademico;
import pe.edu.eduspace.planificacion.entity.EstadoPeriodo;
import pe.edu.eduspace.planificacion.event.AnioActivadoEvent;
import pe.edu.eduspace.planificacion.exception.BusinessRuleException;
import pe.edu.eduspace.planificacion.mapper.PlanificacionMapper;
import pe.edu.eduspace.planificacion.repository.AnioAcademicoRepository;
import pe.edu.eduspace.planificacion.service.impl.AnioAcademicoServiceImpl;

@ExtendWith(MockitoExtension.class)
class AnioAcademicoServiceTest {

    @Mock AnioAcademicoRepository repository;
    @Mock PlanificacionMapper mapper;
    @Mock ApplicationEventPublisher eventos;
    AnioAcademicoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AnioAcademicoServiceImpl(repository, mapper, eventos);
    }

    private AnioAcademico anio(Long id, String nombre, EstadoPeriodo estado) {
        AnioAcademico a = new AnioAcademico();
        a.setId(id);
        a.setNombre(nombre);
        a.setFechaInicio(LocalDate.of(2026, 3, 1));
        a.setFechaFin(LocalDate.of(2026, 12, 20));
        a.setEstado(estado);
        return a;
    }

    @Test
    void noActivaUnAnioMientrasOtroSigueActivo() {
        when(repository.findById(2L)).thenReturn(Optional.of(anio(2L, "2027", EstadoPeriodo.PLANIFICADO)));
        when(repository.findFirstByEstado(EstadoPeriodo.ACTIVO)).thenReturn(Optional.of(anio(1L, "2026", EstadoPeriodo.ACTIVO)));

        assertThatThrownBy(() -> service.activar(2L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("2026")
                .hasMessageContaining("ciérrelo");
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    void activarPublicaElEventoCuandoNoHayOtroActivo() {
        AnioAcademico a = anio(1L, "2026", EstadoPeriodo.PLANIFICADO);
        when(repository.findById(1L)).thenReturn(Optional.of(a));
        when(repository.findFirstByEstado(EstadoPeriodo.ACTIVO)).thenReturn(Optional.empty());

        service.activar(1L);

        assertThat(a.getEstado()).isEqualTo(EstadoPeriodo.ACTIVO);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(AnioActivadoEvent.class);
        AnioActivadoEvent evento = (AnioActivadoEvent) captor.getValue();
        assertThat(evento.eventType()).isEqualTo("anio.activado");
        assertThat(evento.key()).isEqualTo("1");
        assertThat(evento.eventId()).isNotNull();
        assertThat(evento.version()).isEqualTo(1);
    }

    @Test
    void unAnioCerradoNoVuelveAActivarse() {
        when(repository.findById(1L)).thenReturn(Optional.of(anio(1L, "2025", EstadoPeriodo.CERRADO)));
        assertThatThrownBy(() -> service.activar(1L)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void soloSeCierraUnAnioActivo() {
        when(repository.findById(1L)).thenReturn(Optional.of(anio(1L, "2026", EstadoPeriodo.PLANIFICADO)));
        assertThatThrownBy(() -> service.cerrar(1L)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rechazaFechasIncoherentesYNombresRepetidos() {
        assertThatThrownBy(() -> service.crear(new AnioRequest("2026", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 3, 1))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("fecha de fin");
        when(repository.existsByNombre("2026")).thenReturn(true);
        assertThatThrownBy(() -> service.crear(new AnioRequest("2026", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 1))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Ya existe");
    }

    @Test
    void unAnioCerradoEsDeSoloLectura() {
        when(repository.findById(1L)).thenReturn(Optional.of(anio(1L, "2025", EstadoPeriodo.CERRADO)));
        assertThatThrownBy(() -> service.actualizar(1L, new AnioRequest("2025", LocalDate.of(2025, 3, 1), LocalDate.of(2025, 12, 1))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("solo lectura");
    }
}
