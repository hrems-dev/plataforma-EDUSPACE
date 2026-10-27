package pe.edu.eduspace.alumnos.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> contenido, int pagina, int tamano, long total, int totalPaginas) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
