package pe.edu.upeu.eduspace.matricula.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "matricula_requisitos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatriculaRequisito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "matricula_id", nullable = false)
    private Long matriculaId;

    @Column(nullable = false, length = 30)
    private String tipo;

    @Builder.Default
    @Column(nullable = false)
    private Boolean cumplido = false;

    @Column(length = 300)
    private String observacion;

    @Column(name = "verificado_por")
    private Long verificadoPor;

    @Column(name = "verificado_en")
    private LocalDateTime verificadoEn;
}
