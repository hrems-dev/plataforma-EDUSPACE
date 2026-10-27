package pe.edu.eduspace.notificaciones.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "preferencias_notificacion")
@Getter
@Setter
public class PreferenciaNotificacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long usuarioId;
    private String tipo;
    @Enumerated(EnumType.STRING)
    private Canal canal;
    private Boolean habilitada = true;
}
