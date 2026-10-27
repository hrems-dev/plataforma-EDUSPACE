package pe.edu.eduspace.alumnos.exception;

import java.time.OffsetDateTime;
import java.util.List;

public record ErrorResponse(String codigo, String mensaje, String ruta, String traceId,
                            OffsetDateTime fecha, List<String> detalles) {
}
