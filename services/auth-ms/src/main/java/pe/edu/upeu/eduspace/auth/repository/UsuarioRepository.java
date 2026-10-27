package pe.edu.upeu.eduspace.auth.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.upeu.eduspace.auth.entity.Usuario;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @Query("SELECT u FROM Usuario u WHERE LOWER(u.username) = LOWER(:id) OR LOWER(u.email) = LOWER(:id)")
    Optional<Usuario> findByUsernameOrEmail(@Param("id") String identificador);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    /** {@code texto} nunca es nulo (vacío = sin filtro): PostgreSQL no infiere el tipo de un parámetro nulo en LIKE. */
    @Query("""
        SELECT DISTINCT u FROM Usuario u LEFT JOIN u.roles r
        WHERE (:rol = '' OR r.nombre = :rol)
          AND (:activo IS NULL OR u.activo = :activo)
          AND (:texto = '' OR LOWER(u.username) LIKE LOWER(CONCAT('%', :texto, '%'))
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :texto, '%'))
               OR LOWER(CONCAT(u.nombres, ' ', u.apellidos)) LIKE LOWER(CONCAT('%', :texto, '%')))
        """)
    Page<Usuario> buscar(@Param("texto") String texto, @Param("rol") String rol, @Param("activo") Boolean activo, Pageable pageable);

    @Query("SELECT COUNT(DISTINCT u) FROM Usuario u JOIN u.roles r WHERE r.nombre = 'ADMIN' AND u.activo = true")
    long contarAdminsActivos();
}
