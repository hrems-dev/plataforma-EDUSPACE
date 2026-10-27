package pe.edu.eduspace.alumnos.dto;

import java.util.List;

/** Resultado de una importación masiva: cada fila se procesa por separado (una mala no tumba las demás). */
public record ImportacionResponse(int total, int creados, int fallidos, List<Fila> filas) {

    /** {@code linea} es la del archivo (la 1 es el encabezado). */
    public record Fila(int linea, String numeroDocumento, boolean creado, Long alumnoId, String codigo, String error) {
    }
}
