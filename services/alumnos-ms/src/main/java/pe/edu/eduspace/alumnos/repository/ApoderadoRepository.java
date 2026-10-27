package pe.edu.eduspace.alumnos.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.eduspace.alumnos.entity.Apoderado;

public interface ApoderadoRepository extends JpaRepository<Apoderado, Long> {

    /** {@code q} nunca es nulo (texto vacío = sin filtro). */
    @Query("""
            select a from Apoderado a
            where lower(a.nombres) like lower(concat('%', :q, '%'))
               or lower(a.apellidos) like lower(concat('%', :q, '%'))
               or a.numeroDocumento like concat('%', :q, '%')
            """)
    Page<Apoderado> buscar(@Param("q") String q, Pageable pageable);

    Optional<Apoderado> findByTipoDocumentoAndNumeroDocumento(String tipo, String numero);

    boolean existsByTipoDocumentoAndNumeroDocumentoAndIdNot(String tipo, String numero, Long id);

    Optional<Apoderado> findByUsuarioId(Long usuarioId);

    boolean existsByUsuarioIdAndIdNot(Long usuarioId, Long id);
}
