package pe.edu.upeu.eduspace.examenes.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> contenido;
    private int pagina;
    private int tamano;
    private long totalElementos;
    private int totalPaginas;
    private boolean ultima;

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
            .contenido(page.getContent())
            .pagina(page.getNumber())
            .tamano(page.getSize())
            .totalElementos(page.getTotalElements())
            .totalPaginas(page.getTotalPages())
            .ultima(page.isLast())
            .build();
    }
}

