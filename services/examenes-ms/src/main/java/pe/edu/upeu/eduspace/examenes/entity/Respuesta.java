package pe.edu.upeu.eduspace.examenes.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "respuestas", uniqueConstraints = {
    @UniqueConstraint(name = "uk_respuesta", columnNames = {"intento_id", "pregunta_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Respuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intento_id", nullable = false)
    private Intento intento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pregunta_id", nullable = false)
    private Pregunta pregunta;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "opciones_ids", columnDefinition = "bigint[]")
    private List<Long> opcionesIds;

    @Column(name = "texto_respuesta", columnDefinition = "TEXT")
    private String textoRespuesta;

    @Column(name = "puntaje_obtenido", precision = 5, scale = 2)
    private BigDecimal puntajeObtenido;

    @Column(name = "requiere_revision", nullable = false)
    @Builder.Default
    private Boolean requiereRevision = false;

    @Column(name = "revisada_por")
    private Long revisadaPor;

    @Column(name = "guardada_en", nullable = false)
    @Builder.Default
    private LocalDateTime guardadaEn = LocalDateTime.now();
}

