package pe.edu.upeu.eduspace.examenes.dto;

import lombok.Data;

import java.util.List;

@Data
public class GuardarRespuestaRequest {

    private List<Long> opcionesIds;
    private String textoRespuesta;
}

