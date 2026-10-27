package pe.edu.eduspace.alumnos.dto;

import java.time.LocalDateTime;
import pe.edu.eduspace.alumnos.entity.TipoDocumento;

public record DocumentoResponse(Long id, Long alumnoId, TipoDocumento tipo, String nombreArchivo, String contentType,
                                Long tamanoBytes, Boolean verificado, LocalDateTime subidoEn) {
}
