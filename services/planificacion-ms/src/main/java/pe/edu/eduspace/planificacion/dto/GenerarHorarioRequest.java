package pe.edu.eduspace.planificacion.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Pide armar el horario de varias secciones. {@code seccionIds} vacío = todas las del año. {@code reemplazar} borra las clases
 * actuales de esas secciones (si no, solo completa lo que falta). {@code asignarDocentes} asigna por especialidad a los cursos
 * que aún no tienen docente. {@code simular} calcula y devuelve el resultado sin guardar nada.
 */
public record GenerarHorarioRequest(@NotNull Long anioId, List<Long> seccionIds, Boolean reemplazar, Boolean asignarDocentes,
                                    Boolean simular) {
}
