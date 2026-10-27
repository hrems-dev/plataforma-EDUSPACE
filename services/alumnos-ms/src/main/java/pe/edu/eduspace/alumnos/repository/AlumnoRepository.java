package pe.edu.eduspace.alumnos.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.eduspace.alumnos.entity.Alumno;
import pe.edu.eduspace.alumnos.entity.EstadoAlumno;

public interface AlumnoRepository extends JpaRepository<Alumno, Long> {

    /** {@code q} nunca es nulo (texto vacío = sin filtro): PostgreSQL no infiere el tipo de un parámetro nulo en LIKE. */
    @Query("""
            select a from Alumno a
            where lower(a.nombres) like lower(concat('%', :q, '%'))
               or lower(a.apellidoPaterno) like lower(concat('%', :q, '%'))
               or lower(coalesce(a.apellidoMaterno, '')) like lower(concat('%', :q, '%'))
               or a.numeroDocumento like concat('%', :q, '%')
               or lower(a.codigo) like lower(concat('%', :q, '%'))
            """)
    Page<Alumno> buscar(@Param("q") String q, Pageable pageable);

    @Query("""
            select a from Alumno a
            where a.estado = :estado and (
                  lower(a.nombres) like lower(concat('%', :q, '%'))
               or lower(a.apellidoPaterno) like lower(concat('%', :q, '%'))
               or lower(coalesce(a.apellidoMaterno, '')) like lower(concat('%', :q, '%'))
               or a.numeroDocumento like concat('%', :q, '%')
               or lower(a.codigo) like lower(concat('%', :q, '%')))
            """)
    Page<Alumno> buscarPorEstado(@Param("q") String q, @Param("estado") EstadoAlumno estado, Pageable pageable);

    boolean existsByTipoDocumentoAndNumeroDocumento(String tipo, String numero);

    boolean existsByTipoDocumentoAndNumeroDocumentoAndIdNot(String tipo, String numero, Long id);

    boolean existsByUsuarioIdAndIdNot(Long usuarioId, Long id);

    Optional<Alumno> findByUsuarioId(Long usuarioId);

    List<Alumno> findByIdIn(Collection<Long> ids);

    /** Último código generado con ese prefijo (comparación de texto: los correlativos son de ancho fijo). */
    @Query("select max(a.codigo) from Alumno a where a.codigo like concat(:prefijo, '%')")
    Optional<String> ultimoCodigo(@Param("prefijo") String prefijo);
}
