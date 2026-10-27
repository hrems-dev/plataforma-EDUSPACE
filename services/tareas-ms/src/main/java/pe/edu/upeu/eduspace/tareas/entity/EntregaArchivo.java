package pe.edu.upeu.eduspace.tareas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "entrega_archivos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntregaArchivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entrega_id", nullable = false)
    private Long entregaId;

    @Column(name = "nombre_archivo", nullable = false, length = 200)
    private String nombreArchivo;

    @Column(name = "ruta_storage", nullable = false, length = 400)
    private String rutaStorage;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "tamano_bytes")
    private Long tamanoBytes;

    @Builder.Default
    @Column(name = "subido_en", nullable = false)
    private LocalDateTime subidoEn = LocalDateTime.now();
}
