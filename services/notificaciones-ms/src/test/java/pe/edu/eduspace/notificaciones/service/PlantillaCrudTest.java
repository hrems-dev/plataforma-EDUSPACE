package pe.edu.eduspace.notificaciones.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.edu.eduspace.notificaciones.dto.PlantillaCrearRequest;
import pe.edu.eduspace.notificaciones.entity.Canal;
import pe.edu.eduspace.notificaciones.entity.PlantillaNotificacion;
import pe.edu.eduspace.notificaciones.exception.BusinessRuleException;
import pe.edu.eduspace.notificaciones.mapper.NotificacionMapper;
import pe.edu.eduspace.notificaciones.repository.PlantillaRepository;
import pe.edu.eduspace.notificaciones.service.impl.PlantillaServiceImpl;

class PlantillaCrudTest {

    private final PlantillaRepository repository = mock(PlantillaRepository.class);
    private final NotificacionMapper mapper = mock(NotificacionMapper.class);
    private final PlantillaServiceImpl service = new PlantillaServiceImpl(repository, mapper);

    private PlantillaNotificacion plantilla(String codigo) {
        PlantillaNotificacion p = new PlantillaNotificacion();
        p.setCodigo(codigo);
        p.setCanal(Canal.IN_APP);
        p.setCuerpo("x");
        return p;
    }

    @Test
    void creaUnaPlantillaPropiaActivaPorDefecto() {
        when(repository.findByCodigoAndCanal("COMUNICADO_FERIADO", Canal.IN_APP)).thenReturn(Optional.empty());
        when(repository.save(any(PlantillaNotificacion.class))).thenAnswer(i -> i.getArgument(0));
        service.crear(new PlantillaCrearRequest("COMUNICADO_FERIADO", Canal.IN_APP, null, "Hoy no hay clases", null));
        verify(repository).save(any(PlantillaNotificacion.class));
    }

    @Test
    void noPermiteRepetirCodigoYCanal() {
        when(repository.findByCodigoAndCanal("PAGO_VENCIDO", Canal.IN_APP)).thenReturn(Optional.of(plantilla("PAGO_VENCIDO")));
        assertThatThrownBy(() -> service.crear(new PlantillaCrearRequest("PAGO_VENCIDO", Canal.IN_APP, null, "x", true)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Ya existe");
        verify(repository, never()).save(any());
    }

    @Test
    void unaPlantillaDelSistemaNoSeElimina() {
        PlantillaNotificacion p = plantilla("PAGO_CONFIRMADO");
        when(repository.findById(1L)).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.eliminar(1L)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("del sistema");
        verify(repository, never()).delete(any());
    }

    @Test
    void unaPlantillaPropiaSiSeElimina() {
        PlantillaNotificacion p = plantilla("COMUNICADO_FERIADO");
        when(repository.findById(2L)).thenReturn(Optional.of(p));
        service.eliminar(2L);
        verify(repository).delete(p);
    }
}
