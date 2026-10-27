package pe.edu.eduspace.alumnos.dto;

import java.util.List;
import pe.edu.eduspace.alumnos.entity.TipoDocumento;

public record ExpedienteCompletoResponse(boolean completo, List<TipoDocumento> faltantes) {
}
