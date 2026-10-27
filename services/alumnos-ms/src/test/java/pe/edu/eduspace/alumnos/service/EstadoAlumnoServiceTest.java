package pe.edu.eduspace.alumnos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import pe.edu.eduspace.alumnos.dto.CambioEstadoRequest;
import pe.edu.eduspace.alumnos.entity.Alumno;
import pe.edu.eduspace.alumnos.entity.AlumnoEstadoHistorial;
import pe.edu.eduspace.alumnos.entity.EstadoAlumno;
import pe.edu.eduspace.alumnos.event.AlumnoEstadoCambiadoEvent;
import pe.edu.eduspace.alumnos.exception.BusinessRuleException;
import pe.edu.eduspace.alumnos.mapper.AlumnoMapper;
import pe.edu.eduspace.alumnos.repository.AlumnoEstadoHistorialRepository;
import pe.edu.eduspace.alumnos.repository.AlumnoRepository;
import pe.edu.eduspace.alumnos.service.impl.EstadoAlumnoServiceImpl;

@ExtendWith(MockitoExtension.class)
class EstadoAlumnoServiceTest {

    @Mock AlumnoRepository repository;
    @Mock AlumnoEstadoHistorialRepository historialRepository;
    @Mock AlumnoMapper mapper;
    @Mock ApplicationEventPublisher eventos;

    private Alumno alumno(EstadoAlumno estado) {
        Alumno a = new Alumno();
        a.setId(4L);
        a.setEstado(estado);
        return a;
    }

    @Test
    void elCambioDeEstadoRegistraHistorialYPublicaElEvento() {
        var service = new EstadoAlumnoServiceImpl(repository, historialRepository, mapper, eventos);
        Alumno alumno = alumno(EstadoAlumno.ACTIVO);
        when(repository.findById(4L)).thenReturn(Optional.of(alumno));

        service.cambiar(4L, new CambioEstadoRequest(EstadoAlumno.RETIRADO, "Traslado de ciudad"), 55L);

        assertThat(alumno.getEstado()).isEqualTo(EstadoAlumno.RETIRADO);
        ArgumentCaptor<AlumnoEstadoHistorial> historial = ArgumentCaptor.forClass(AlumnoEstadoHistorial.class);
        verify(historialRepository).save(historial.capture());
        assertThat(historial.getValue().getEstadoAnterior()).isEqualTo(EstadoAlumno.ACTIVO);
        assertThat(historial.getValue().getEstadoNuevo()).isEqualTo(EstadoAlumno.RETIRADO);
        assertThat(historial.getValue().getUsuarioId()).isEqualTo(55L);
        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(evento.capture());
        var e = (AlumnoEstadoCambiadoEvent) evento.getValue();
        assertThat(e.estadoAnterior()).isEqualTo("ACTIVO");
        assertThat(e.estadoNuevo()).isEqualTo("RETIRADO");
        assertThat(e.motivo()).isEqualTo("Traslado de ciudad");
        assertThat(e.eventType()).isEqualTo("alumno.estado-cambiado");
    }

    @Test
    void noSeCambiaAlMismoEstado() {
        var service = new EstadoAlumnoServiceImpl(repository, historialRepository, mapper, eventos);
        when(repository.findById(4L)).thenReturn(Optional.of(alumno(EstadoAlumno.ACTIVO)));
        assertThatThrownBy(() -> service.cambiar(4L, new CambioEstadoRequest(EstadoAlumno.ACTIVO, "x"), 1L))
                .isInstanceOf(BusinessRuleException.class);
        verify(historialRepository, never()).save(any());
        verify(eventos, never()).publishEvent(any(Object.class));
    }
}
