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
@Table(name = "cierres_periodo")
public class CierrePeriodo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seccion_id", nullable = false)
    private Long seccionId;

    @Column(name = "periodo_id", nullable = false)
    private Long periodoId;

    @Column(name = "estado", nullable = false, length = 10)
    private String estado;

    @Column(name = "cerrado_por")
    private Long cerradoPor;

    @Column(name = "cerrado_en")
    private LocalDateTime cerradoEn;

    @Column(name = "reabierto_por")
    private Long reabiertoPor;

    @Column(name = "reabierto_en")
    private LocalDateTime reabiertoEn;

    @Column(name = "motivo_reapertura", length = 300)
    private String motivoReapertura;
}

