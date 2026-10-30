package pe.edu.upeu.eduspace.tareas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Material de un curso para una sección: un archivo (en MinIO) o un enlace. */
@Entity
@Table(name = "materiales")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Material {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anio_id", nullable = false)
    private Long anioId;

    @Column(name = "seccion_id", nullable = false)
    private Long seccionId;

    @Column(name = "curso_id", nullable = false)
    private Long cursoId;

    @Column(name = "docente_id", nullable = false)
    private Long docenteId;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(length = 1000)
    private String descripcion;

    @Column(nullable = false, length = 8)
    private String tipo;

    @Column(length = 500)
    private String enlace;

    @Column(name = "nombre_archivo", length = 200)
    private String nombreArchivo;

    @Column(name = "ruta_storage", length = 400)
    private String rutaStorage;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "tamano_bytes")
    private Long tamanoBytes;

    @Builder.Default
    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();
}
