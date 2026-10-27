package pe.edu.upeu.eduspace.calificaciones.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "boletines")
public class Boletin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "matricula_id", nullable = false)
    private Long matriculaId;

    @Column(name = "alumno_id", nullable = false)
    private Long alumnoId;

    @Column(name = "periodo_id")
    private Long periodoId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "estado", nullable = false, length = 8)
    private String estado;

    @Column(name = "ruta_pdf", nullable = false, length = 400)
    private String rutaPdf;

    @Column(name = "generado_por")
    private Long generadoPor;

    @Column(name = "generado_en", nullable = false)
    private LocalDateTime generadoEn;
}

