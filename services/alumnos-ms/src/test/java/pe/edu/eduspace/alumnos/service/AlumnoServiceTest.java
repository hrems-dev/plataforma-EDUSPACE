package pe.edu.eduspace.alumnos.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import pe.edu.eduspace.alumnos.dto.AlumnoCreateRequest;
import pe.edu.eduspace.alumnos.dto.ApoderadoRequest;
import pe.edu.eduspace.alumnos.dto.VinculoApoderadoRequest;
import pe.edu.eduspace.alumnos.entity.Parentesco;
import pe.edu.eduspace.alumnos.exception.BusinessRuleException;
import pe.edu.eduspace.alumnos.exception.DatosInvalidosException;
import pe.edu.eduspace.alumnos.exception.DuplicateResourceException;
import pe.edu.eduspace.alumnos.mapper.AlumnoMapper;
import pe.edu.eduspace.alumnos.repository.AlumnoApoderadoRepository;
import pe.edu.eduspace.alumnos.repository.AlumnoRepository;
import pe.edu.eduspace.alumnos.service.impl.AlumnoServiceImpl;

/** Reglas del alta que se rechazan antes de tocar la base: nada se guarda ni se publica. */
@ExtendWith(MockitoExtension.class)
class AlumnoServiceTest {

    @Mock AlumnoRepository repository;
    @Mock AlumnoApoderadoRepository vinculoRepository;
    @Mock ApoderadoService apoderadoService;
    @Mock AlumnoMapper mapper;
    @Mock ApplicationEventPublisher eventos;
    @Mock EntityManager entityManager;
    AlumnoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AlumnoServiceImpl(repository, vinculoRepository, apoderadoService, mapper, eventos, entityManager);
    }

    private VinculoApoderadoRequest apoderado(String dni, Parentesco parentesco, Boolean principal, Boolean pago) {
        return new VinculoApoderadoRequest(null,
                new ApoderadoRequest("DNI", dni, "Nombre", "Apellido", null, null, null, null, null),
                parentesco, principal, pago, null, null);
    }

    private AlumnoCreateRequest alta(String documento, List<VinculoApoderadoRequest> apoderados) {
        return new AlumnoCreateRequest("DNI", documento, "Luis", "Ramos", "Pérez", LocalDate.of(2018, 5, 10), "M",
                null, null, null, null, null, null, apoderados);
    }

    private void assertSinEfectos() {
        verify(repository, never()).save(any());
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    void exigeUnApoderadoPrincipal() {
        var request = alta("70123456", List.of(apoderado("40111111", Parentesco.PADRE, false, null),
                apoderado("40222222", Parentesco.MADRE, null, null)));
        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exactamente un apoderado principal (se marcaron 0)");
        assertSinEfectos();
    }

    @Test
    void rechazaDosPrincipales() {
        var request = alta("70123456", List.of(apoderado("40111111", Parentesco.PADRE, true, null),
                apoderado("40222222", Parentesco.MADRE, true, null)));
        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("se marcaron 2");
        assertSinEfectos();
    }

    @Test
    void rechazaDosResponsablesDePago() {
        var request = alta("70123456", List.of(apoderado("40111111", Parentesco.PADRE, true, true),
                apoderado("40222222", Parentesco.MADRE, false, true)));
        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("un responsable de pago");
        assertSinEfectos();
    }

    @Test
    void rechazaElMismoApoderadoDosVeces() {
        var request = alta("70123456", List.of(apoderado("40111111", Parentesco.PADRE, true, null),
                apoderado("40111111", Parentesco.TUTOR, false, null)));
        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("dos veces");
        assertSinEfectos();
    }

    @Test
    void conUnSoloApoderadoSinMarcarSeAsumePrincipal() {
        // un único apoderado sin bandera no falla por "principal": llega hasta el candado de códigos (aquí simulado sin BD)
        var request = alta("70123456", List.of(apoderado("40111111", Parentesco.PADRE, null, null)));
        when(entityManager.createNativeQuery(org.mockito.ArgumentMatchers.anyString())).thenThrow(new IllegalStateException("fin"));
        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(IllegalStateException.class).hasMessage("fin");
    }

    @Test
    void documentoDeAlumnoRepetido() {
        when(repository.existsByTipoDocumentoAndNumeroDocumento("DNI", "70123456")).thenReturn(true);
        var request = alta("70123456", List.of(apoderado("40111111", Parentesco.PADRE, true, null)));
        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("70123456");
        assertSinEfectos();
    }

    @Test
    void dniConFormatoInvalido() {
        var request = alta("123", List.of(apoderado("40111111", Parentesco.PADRE, true, null)));
        assertThatThrownBy(() -> service.crear(request)).isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("formato de un DNI");
        assertSinEfectos();
    }

    @Test
    void cadaApoderadoIndicaIdODatosPeroNoAmbos() {
        var ambos = new VinculoApoderadoRequest(5L, new ApoderadoRequest("DNI", "40111111", "N", "A", null, null, null, null, null),
                Parentesco.PADRE, true, null, null, null);
        assertThatThrownBy(() -> service.crear(alta("70123456", List.of(ambos)))).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("apoderadoId o sus datos");
    }
}
