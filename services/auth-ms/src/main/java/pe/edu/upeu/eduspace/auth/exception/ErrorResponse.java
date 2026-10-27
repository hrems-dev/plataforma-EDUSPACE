package pe.edu.upeu.eduspace.auth.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private int codigo;
    private String mensaje;
    private String ruta;
    private String traceId;
    private LocalDateTime fecha;
    private List<String> detalles;
}

