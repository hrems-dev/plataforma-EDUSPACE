package pe.edu.eduspace.planificacion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Agrega varios cursos a la malla de varios grados de una vez (p. ej. los cursos de Primaria a los 6 grados). */
public record MallaLoteRequest(@NotEmpty List<Long> gradoIds, @NotEmpty List<@Valid ItemLote> cursos, Boolean sobrescribir) {

    public record ItemLote(@NotNull Long cursoId, @NotNull @Min(1) @Max(40) Short horasSemanales) {
    }
}
