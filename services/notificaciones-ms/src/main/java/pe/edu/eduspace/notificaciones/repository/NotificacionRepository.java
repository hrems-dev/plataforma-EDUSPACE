package pe.edu.eduspace.notificaciones.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.eduspace.notificaciones.entity.Canal;
import pe.edu.eduspace.notificaciones.entity.EstadoNotificacion;
import pe.edu.eduspace.notificaciones.entity.Notificacion;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long>, JpaSpecificationExecutor<Notificacion> {

    Page<Notificacion> findByUsuarioIdAndCanalOrderByCreadaEnDescIdDesc(Long usuarioId, Canal canal, Pageable pageable);

    Page<Notificacion> findByUsuarioIdAndCanalAndLeidaOrderByCreadaEnDescIdDesc(Long usuarioId, Canal canal, Boolean leida,
                                                                                Pageable pageable);

    long countByUsuarioIdAndCanalAndLeidaFalse(Long usuarioId, Canal canal);

    Optional<Notificacion> findByIdAndUsuarioId(Long id, Long usuarioId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Notificacion n set n.leida = true, n.leidaEn = current_timestamp
            where n.usuarioId = :usuarioId and n.canal = :canal and n.leida = false
            """)
    int marcarTodasLeidas(@Param("usuarioId") Long usuarioId, @Param("canal") Canal canal);

    /** Pendientes o fallidas con menos de {@code maxIntentos} intentos registrados, las más antiguas primero. */
    @Query("""
            select n from Notificacion n
            where n.estado in :estados
              and (select count(i) from IntentoEnvio i where i.notificacion.id = n.id) < :maxIntentos
            order by n.creadaEn, n.id
            """)
    List<Notificacion> pendientes(@Param("estados") List<EstadoNotificacion> estados,
                                  @Param("maxIntentos") long maxIntentos, Pageable pageable);
}
