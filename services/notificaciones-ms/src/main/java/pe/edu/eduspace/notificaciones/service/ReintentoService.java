package pe.edu.eduspace.notificaciones.service;

/** Toma lo pendiente y lo fallido y lo vuelve a intentar; también limpia los eventos procesados antiguos. */
public interface ReintentoService {
    /** @return cuántas notificaciones se intentaron en este ciclo */
    int procesarPendientes();

    /** @return cuántos registros de {@code eventos_procesados} se borraron */
    int limpiarEventosProcesados();
}
