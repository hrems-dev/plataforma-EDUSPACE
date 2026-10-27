package pe.edu.eduspace.alumnos.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.eduspace.alumnos.entity.AlumnoApoderado;

public interface AlumnoApoderadoRepository extends JpaRepository<AlumnoApoderado, Long> {

    @Query("select v from AlumnoApoderado v join fetch v.apoderado where v.alumno.id = :alumnoId order by v.id")
    List<AlumnoApoderado> findByAlumno(@Param("alumnoId") Long alumnoId);

    @Query("select v from AlumnoApoderado v join fetch v.alumno where v.apoderado.id = :apoderadoId order by v.alumno.apellidoPaterno, v.alumno.nombres")
    List<AlumnoApoderado> findByApoderado(@Param("apoderadoId") Long apoderadoId);

    @Query("select v from AlumnoApoderado v join fetch v.alumno where v.apoderado.usuarioId = :usuarioId order by v.alumno.apellidoPaterno, v.alumno.nombres")
    List<AlumnoApoderado> findByUsuarioDelApoderado(@Param("usuarioId") Long usuarioId);

    Optional<AlumnoApoderado> findByIdAndAlumnoId(Long id, Long alumnoId);

    boolean existsByAlumnoIdAndApoderadoId(Long alumnoId, Long apoderadoId);

    boolean existsByAlumnoIdAndApoderadoUsuarioId(Long alumnoId, Long usuarioId);

    long countByAlumnoId(Long alumnoId);

    @Query("select v from AlumnoApoderado v join fetch v.apoderado where v.alumno.id = :alumnoId and v.esResponsablePago = true")
    Optional<AlumnoApoderado> findResponsablePago(@Param("alumnoId") Long alumnoId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AlumnoApoderado v set v.esPrincipal = false where v.alumno.id = :alumnoId and v.id <> :excluirId and v.esPrincipal = true")
    int quitarPrincipalDeLosDemas(@Param("alumnoId") Long alumnoId, @Param("excluirId") Long excluirId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AlumnoApoderado v set v.esResponsablePago = false where v.alumno.id = :alumnoId and v.id <> :excluirId and v.esResponsablePago = true")
    int quitarResponsablePagoDeLosDemas(@Param("alumnoId") Long alumnoId, @Param("excluirId") Long excluirId);
}
