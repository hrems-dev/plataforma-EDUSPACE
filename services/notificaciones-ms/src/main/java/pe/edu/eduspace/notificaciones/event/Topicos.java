package pe.edu.eduspace.notificaciones.event;

/** Tópicos que consume este servicio (catálogo del informe general §3.2 y adenda A5). */
public final class Topicos {

    public static final String MATRICULA_CREADA = "matricula.creada";
    public static final String MATRICULA_CONFIRMADA = "matricula.confirmada";
    public static final String MATRICULA_ANULADA = "matricula.anulada";
    public static final String PAGO_CONFIRMADO = "pago.confirmado";
    public static final String PAGO_VENCIDO = "pago.vencido";
    public static final String PAGO_POR_VENCER = "pago.por-vencer";
    public static final String ASISTENCIA_FALTA = "asistencia.falta-registrada";
    public static final String TAREA_PUBLICADA = "tarea.publicada";
    public static final String TAREA_RECORDATORIO = "tarea.recordatorio";
    public static final String TAREA_CALIFICADA = "tarea.calificada";
    public static final String MATERIAL_PUBLICADO = "material.publicado";
    public static final String EXAMEN_PUBLICADO = "examen.publicado";
    public static final String EXAMEN_CALIFICADO = "examen.calificado";
    public static final String BOLETIN_GENERADO = "boletin.generado";
    public static final String HORARIO_PUBLICADO = "horario.publicado";
    public static final String JUSTIFICACION_RESUELTA = "justificacion.resuelta";

    private Topicos() {
    }
}
