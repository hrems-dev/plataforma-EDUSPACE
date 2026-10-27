package pe.edu.eduspace.planificacion.event;

/** Evento de dominio publicado a Kafka. El tipo coincide con el nombre del tópico (sobre estándar, adenda A1). */
public interface DomainEvent {
    String eventType();

    /** Clave del mensaje = id de la entidad principal (garantiza orden por entidad). */
    String key();
}
