package pe.edu.upeu.eduspace.calificaciones.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "parametros_evaluacion")
public class ParametroEvaluacion {

    @Id
    @Column(name = "clave", length = 40)
    private String clave;

    @Column(name = "valor", nullable = false, length = 40)
    private String valor;

    @Column(name = "detalle", length = 200)
    private String detalle;
}

