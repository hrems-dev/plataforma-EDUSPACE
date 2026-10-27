package pe.edu.eduspace.alumnos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.eduspace.alumnos.config.DocumentosProperties;
import pe.edu.eduspace.alumnos.entity.Alumno;
import pe.edu.eduspace.alumnos.entity.AlumnoDocumento;
import pe.edu.eduspace.alumnos.entity.TipoDocumento;
import pe.edu.eduspace.alumnos.exception.DatosInvalidosException;
import pe.edu.eduspace.alumnos.mapper.AlumnoMapper;
import pe.edu.eduspace.alumnos.repository.AlumnoDocumentoRepository;
import pe.edu.eduspace.alumnos.repository.AlumnoRepository;
import pe.edu.eduspace.alumnos.service.impl.DocumentoServiceImpl;
import pe.edu.eduspace.alumnos.storage.AlmacenamientoArchivos;

/** Subida de documentos con almacenamiento simulado. */
@ExtendWith(MockitoExtension.class)
class DocumentoServiceTest {

    @Mock AlumnoDocumentoRepository repository;
    @Mock AlumnoRepository alumnoRepository;
    @Mock AlmacenamientoArchivos almacenamiento;
    @Mock AlumnoMapper mapper;
    DocumentoServiceImpl service;
    final InputStream contenido = new ByteArrayInputStream(new byte[]{1, 2, 3});

    @BeforeEach
    void setUp() {
        service = new DocumentoServiceImpl(repository, alumnoRepository, almacenamiento,
                new DocumentosProperties(1024L, List.of("application/pdf", "image/png"), Duration.ofMinutes(10)), mapper);
        Alumno alumno = new Alumno();
        alumno.setId(1L);
        org.mockito.Mockito.lenient().when(alumnoRepository.findById(1L)).thenReturn(Optional.of(alumno));
    }

    @Test
    void guardaElArchivoConUnaClaveSegura_yRegistraLosMetadatos() {
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        service.subir(1L, TipoDocumento.DNI, "../../mi dni (1).pdf", "application/pdf", 3, contenido);

        ArgumentCaptor<String> clave = ArgumentCaptor.forClass(String.class);
        verify(almacenamiento).guardar(clave.capture(), eq(contenido), eq(3L), eq("application/pdf"));
        assertThat(clave.getValue()).startsWith("alumnos/1/").endsWith("-mi_dni__1_.pdf").doesNotContain("..");
        ArgumentCaptor<AlumnoDocumento> doc = ArgumentCaptor.forClass(AlumnoDocumento.class);
        verify(repository).saveAndFlush(doc.capture());
        assertThat(doc.getValue().getRutaStorage()).isEqualTo(clave.getValue());
        assertThat(doc.getValue().getVerificado()).isFalse();
        assertThat(doc.getValue().getTipo()).isEqualTo(TipoDocumento.DNI);
    }

    @Test
    void rechazaArchivoVacioGrandeOTipoNoPermitido_sinTocarElAlmacenamiento() {
        assertThatThrownBy(() -> service.subir(1L, TipoDocumento.DNI, "a.pdf", "application/pdf", 0, contenido))
                .isInstanceOf(DatosInvalidosException.class).hasMessageContaining("vacío");
        assertThatThrownBy(() -> service.subir(1L, TipoDocumento.DNI, "a.pdf", "application/pdf", 2048, contenido))
                .isInstanceOf(DatosInvalidosException.class).hasMessageContaining("supera el máximo");
        assertThatThrownBy(() -> service.subir(1L, TipoDocumento.DNI, "a.exe", "application/x-msdownload", 10, contenido))
                .isInstanceOf(DatosInvalidosException.class).hasMessageContaining("no permitido");
        verify(almacenamiento, never()).guardar(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void siFallaLaBaseSeBorraElArchivoHuerfano() {
        when(repository.saveAndFlush(any())).thenThrow(new IllegalStateException("BD caída"));
        assertThatThrownBy(() -> service.subir(1L, TipoDocumento.DNI, "a.pdf", "application/pdf", 3, contenido))
                .isInstanceOf(IllegalStateException.class);
        ArgumentCaptor<String> subida = ArgumentCaptor.forClass(String.class);
        verify(almacenamiento).guardar(subida.capture(), any(), anyLong(), anyString());
        verify(almacenamiento).eliminar(subida.getValue());
    }

    @Test
    void laDescargaDevuelveUnaUrlFirmadaConVencimiento() {
        AlumnoDocumento doc = new AlumnoDocumento();
        doc.setRutaStorage("alumnos/1/x-a.pdf");
        doc.setNombreArchivo("a.pdf");
        when(repository.findByIdAndAlumnoId(9L, 1L)).thenReturn(Optional.of(doc));
        when(almacenamiento.urlDescarga("alumnos/1/x-a.pdf", Duration.ofMinutes(10))).thenReturn("http://minio/firmada");

        var respuesta = service.descargar(1L, 9L);
        assertThat(respuesta.url()).isEqualTo("http://minio/firmada");
        assertThat(respuesta.expiraEnSegundos()).isEqualTo(600);
    }
}
