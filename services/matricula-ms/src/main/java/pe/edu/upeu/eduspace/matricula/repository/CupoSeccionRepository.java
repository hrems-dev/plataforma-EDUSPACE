package pe.edu.upeu.eduspace.matricula.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.upeu.eduspace.matricula.entity.CupoSeccion;

import java.util.List;

public interface CupoSeccionRepository extends JpaRepository<CupoSeccion, Long> {

    /** Reserva atómica: solo incrementa si queda cupo. Devuelve filas afectadas (0 = sin cupo). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CupoSeccion c SET c.ocupados = c.ocupados + 1, c.actualizadoEn = CURRENT_TIMESTAMP "
         + "WHERE c.seccionId = :seccionId AND c.ocupados < c.capacidad")
    int reservar(@Param("seccionId") Long seccionId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CupoSeccion c SET c.ocupados = c.ocupados - 1, c.actualizadoEn = CURRENT_TIMESTAMP "
         + "WHERE c.seccionId = :seccionId AND c.ocupados > 0")
    int liberar(@Param("seccionId") Long seccionId);

    List<CupoSeccion> findByAnioIdAndGradoIdOrderBySeccionNombre(Long anioId, Long gradoId);

    List<CupoSeccion> findByAnioIdOrderByGradoIdAscSeccionNombreAsc(Long anioId);

    /** Alta idempotente (varias solicitudes simultáneas a una sección nueva no chocan). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO cupos_seccion (seccion_id, anio_id, nivel_id, grado_id, seccion_nombre, capacidad, ocupados, actualizado_en) "
        + "VALUES (:id, :anioId, :nivelId, :gradoId, :nombre, :capacidad, 0, now()) ON CONFLICT (seccion_id) DO NOTHING", nativeQuery = true)
    int insertarSiNoExiste(@Param("id") Long id, @Param("anioId") Long anioId, @Param("nivelId") Long nivelId,
                           @Param("gradoId") Long gradoId, @Param("nombre") String nombre, @Param("capacidad") short capacidad);

    /** Ajusta la capacidad cuando planificación la cambia, sin dejarla por debajo de lo ocupado. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CupoSeccion c SET c.capacidad = :capacidad, c.actualizadoEn = CURRENT_TIMESTAMP "
         + "WHERE c.seccionId = :id AND c.capacidad <> :capacidad AND c.ocupados <= :capacidad")
    int ajustarCapacidad(@Param("id") Long id, @Param("capacidad") short capacidad);
}
