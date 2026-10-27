package pe.edu.upeu.eduspace.tareas.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tarea_adjuntos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TareaAdjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tarea_id", nullable = false)
    private Long tareaId;

    @Column(name = "nombre_archivo", nullable = false, length = 200)
    private String nombreArchivo;

    @Column(name = "ruta_storage", nullable = false, length = 400)
    private String rutaStorage;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "tamano_bytes")
    private Long tamanoBytes;
}
