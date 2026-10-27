package pe.edu.eduspace.alumnos.service;

import java.io.InputStream;
import java.util.List;
import pe.edu.eduspace.alumnos.dto.DescargaResponse;
import pe.edu.eduspace.alumnos.dto.DocumentoResponse;
import pe.edu.eduspace.alumnos.entity.TipoDocumento;

public interface DocumentoService {
    List<DocumentoResponse> listar(Long alumnoId);

    DocumentoResponse subir(Long alumnoId, TipoDocumento tipo, String nombreArchivo, String contentType, long tamano,
                            InputStream contenido);

    DescargaResponse descargar(Long alumnoId, Long documentoId);

    DocumentoResponse verificar(Long alumnoId, Long documentoId, boolean verificado);

    void eliminar(Long alumnoId, Long documentoId);
}
