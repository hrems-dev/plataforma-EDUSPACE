package pe.edu.eduspace.alumnos.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "alumno_documentos")
@Getter
@Setter
public class AlumnoDocumento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alumno_id")
    private Alumno alumno;
    @Enumerated(EnumType.STRING)
    private TipoDocumento tipo;
    private String nombreArchivo;
    /** Clave del objeto en MinIO/S3. */
    private String rutaStorage;
    private String contentType;
    private Long tamanoBytes;
    private Boolean verificado = false;
    private LocalDateTime subidoEn;

    @PrePersist
    void onCreate() {
        if (subidoEn == null) {
            subidoEn = LocalDateTime.now();
        }
    }
}
