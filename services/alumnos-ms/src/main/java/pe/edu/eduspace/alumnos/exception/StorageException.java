package pe.edu.eduspace.alumnos.exception;

/** Fallo del almacenamiento de archivos (MinIO/S3): HTTP 502. */
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
