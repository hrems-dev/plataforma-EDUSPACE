package pe.edu.upeu.eduspace.calificaciones.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotasLoteRequest {

    @NotNull(message = "seccionId es obligatorio")
    private Long seccionId;

    @NotNull(message = "cursoId es obligatorio")
    private Long cursoId;

    @NotNull(message = "periodoId es obligatorio")
    private Long periodoId;

    @NotNull(message = "criterioId es obligatorio")
    private Long criterioId;

    @NotEmpty(message = "La lista de notas no puede estar vacía")
    @Valid
    private List<NotaItemRequest> notas;
}

