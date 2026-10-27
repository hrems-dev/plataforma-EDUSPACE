package pe.edu.upeu.eduspace.tareas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import pe.edu.upeu.eduspace.tareas.entity.EntregaArchivo;

import java.util.List;

public interface EntregaArchivoRepository extends JpaRepository<EntregaArchivo, Long> {

    List<EntregaArchivo> findByEntregaIdOrderByIdAsc(Long entregaId);

    @Modifying
    void deleteByEntregaId(Long entregaId);
}
