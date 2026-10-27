package pe.edu.eduspace.alumnos.service.impl;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.alumnos.config.DocumentosProperties;
import pe.edu.eduspace.alumnos.dto.DescargaResponse;
import pe.edu.eduspace.alumnos.dto.DocumentoResponse;
import pe.edu.eduspace.alumnos.entity.Alumno;
import pe.edu.eduspace.alumnos.entity.AlumnoDocumento;
import pe.edu.eduspace.alumnos.entity.TipoDocumento;
import pe.edu.eduspace.alumnos.exception.DatosInvalidosException;
import pe.edu.eduspace.alumnos.exception.ResourceNotFoundException;
import pe.edu.eduspace.alumnos.mapper.AlumnoMapper;
import pe.edu.eduspace.alumnos.repository.AlumnoDocumentoRepository;
import pe.edu.eduspace.alumnos.repository.AlumnoRepository;
import pe.edu.eduspace.alumnos.service.DocumentoService;
import pe.edu.eduspace.alumnos.storage.AlmacenamientoArchivos;

@Service
@RequiredArgsConstructor
@Transactional
public class DocumentoServiceImpl implements DocumentoService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoServiceImpl.class);

    private final AlumnoDocumentoRepository repository;
    private final AlumnoRepository alumnoRepository;
    private final AlmacenamientoArchivos almacenamiento;
    private final DocumentosProperties props;
    private final AlumnoMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<DocumentoResponse> listar(Long alumnoId) {
        existeAlumno(alumnoId);
        return repository.findByAlumnoIdOrderBySubidoEnDesc(alumnoId).stream().map(mapper::toResponse).toList();
    }

    @Override
    public DocumentoResponse subir(Long alumnoId, TipoDocumento tipo, String nombreArchivo, String contentType,
                                   long tamano, InputStream contenido) {
        Alumno alumno = alumnoRepository.findById(alumnoId).orElseThrow(() -> ResourceNotFoundException.of("Alumno", alumnoId));
        validar(contentType, tamano);
        String nombre = limpiar(nombreArchivo);
        String clave = "alumnos/" + alumnoId + "/" + UUID.randomUUID() + "-" + nombre;
        almacenamiento.guardar(clave, contenido, tamano, contentType);

        AlumnoDocumento doc = new AlumnoDocumento();
        doc.setAlumno(alumno);
        doc.setTipo(tipo);
        doc.setNombreArchivo(nombre);
        doc.setRutaStorage(clave);
        doc.setContentType(contentType);
        doc.setTamanoBytes(tamano);
        doc.setVerificado(false);
        try {
            return mapper.toResponse(repository.saveAndFlush(doc));
        } catch (RuntimeException ex) {
            // el archivo ya está en el almacenamiento: se compensa para no dejar huérfanos
            try {
                almacenamiento.eliminar(clave);
            } catch (RuntimeException limpieza) {
                log.warn("component=DocumentoService eventType=Compensacion status=ERROR key={} cause={}", clave, limpieza.getMessage());
            }
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DescargaResponse descargar(Long alumnoId, Long documentoId) {
        AlumnoDocumento doc = buscar(alumnoId, documentoId);
        String url = almacenamiento.urlDescarga(doc.getRutaStorage(), props.vigenciaDescarga());
        return new DescargaResponse(url, props.vigenciaDescarga().toSeconds(), doc.getNombreArchivo());
    }

    @Override
    public DocumentoResponse verificar(Long alumnoId, Long documentoId, boolean verificado) {
        AlumnoDocumento doc = buscar(alumnoId, documentoId);
        doc.setVerificado(verificado);
        return mapper.toResponse(repository.save(doc));
    }

    @Override
    public void eliminar(Long alumnoId, Long documentoId) {
        AlumnoDocumento doc = buscar(alumnoId, documentoId);
        almacenamiento.eliminar(doc.getRutaStorage());
        repository.delete(doc);
    }

    private void validar(String contentType, long tamano) {
        if (tamano <= 0) {
            throw new DatosInvalidosException("El archivo está vacío");
        }
        if (tamano > props.maxBytes()) {
            throw new DatosInvalidosException("El archivo supera el máximo de " + props.maxBytes() / (1024 * 1024) + " MB");
        }
        if (contentType == null || !props.tiposPermitidos().contains(contentType.toLowerCase())) {
            throw new DatosInvalidosException("Tipo de archivo no permitido: " + contentType + " (permitidos: "
                    + String.join(", ", props.tiposPermitidos()) + ")");
        }
    }

    /** Deja solo caracteres seguros: el nombre forma parte de la clave del objeto. */
    private String limpiar(String nombre) {
        String base = nombre == null || nombre.isBlank() ? "archivo" : nombre.substring(Math.max(nombre.lastIndexOf('/'), nombre.lastIndexOf('\\')) + 1);
        String seguro = base.replaceAll("[^A-Za-z0-9._-]", "_");
        return seguro.length() > 100 ? seguro.substring(seguro.length() - 100) : seguro;
    }

    private AlumnoDocumento buscar(Long alumnoId, Long documentoId) {
        return repository.findByIdAndAlumnoId(documentoId, alumnoId)
                .orElseThrow(() -> ResourceNotFoundException.of("Documento", documentoId));
    }

    private void existeAlumno(Long alumnoId) {
        if (!alumnoRepository.existsById(alumnoId)) {
            throw ResourceNotFoundException.of("Alumno", alumnoId);
        }
    }
}
