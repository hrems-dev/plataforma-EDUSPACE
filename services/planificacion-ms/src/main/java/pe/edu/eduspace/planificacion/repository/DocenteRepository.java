package pe.edu.eduspace.planificacion.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.eduspace.planificacion.entity.Docente;

public interface DocenteRepository extends JpaRepository<Docente, Long> {
    @Query("""
            select d from Docente d
            where lower(d.apellidos) like lower(concat('%', :q, '%'))
               or lower(d.nombres) like lower(concat('%', :q, '%'))
               or d.numeroDocumento like concat('%', :q, '%')
            """)
    /** {@code q} nunca es nulo (texto vacío = sin filtro): PostgreSQL no infiere el tipo de un parámetro nulo en LIKE. */
    Page<Docente> buscar(@Param("q") String q, Pageable pageable);

    Optional<Docente> findByUsuarioId(Long usuarioId);

    boolean existsByTipoDocumentoAndNumeroDocumento(String tipo, String numero);

    boolean existsByTipoDocumentoAndNumeroDocumentoAndIdNot(String tipo, String numero, Long id);

    boolean existsByUsuarioIdAndIdNot(Long usuarioId, Long id);
}
