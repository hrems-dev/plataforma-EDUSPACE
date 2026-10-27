package pe.edu.eduspace.alumnos.storage;

import java.io.InputStream;
import java.time.Duration;

/** Puerto de almacenamiento de archivos del expediente (MinIO/S3). Los errores se reportan como StorageException. */
public interface AlmacenamientoArchivos {

    void guardar(String clave, InputStream contenido, long tamano, String contentType);

    /** URL firmada de descarga con vencimiento corto. */
    String urlDescarga(String clave, Duration vigencia);

    void eliminar(String clave);
}
