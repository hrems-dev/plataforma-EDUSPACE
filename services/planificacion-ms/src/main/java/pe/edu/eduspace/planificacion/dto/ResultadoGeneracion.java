package pe.edu.eduspace.planificacion.dto;

import java.util.List;

public record ResultadoGeneracion(boolean simulacion, int seccionesGeneradas, int seccionesOmitidas, int horasColocadas, int horasSinColocar,
                                  int docentesAsignados, List<ResultadoSeccionGeneracion> secciones) {
}
