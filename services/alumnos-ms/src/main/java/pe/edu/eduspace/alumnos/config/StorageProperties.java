package pe.edu.eduspace.alumnos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexión a MinIO. {@code publicEndpoint} es el host que el navegador puede alcanzar: con él se firman las
 * URL de descarga (no el nombre interno de Docker).
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String endpoint, String publicEndpoint, String accessKey, String secretKey,
                                String bucket, Boolean crearBucketAlArrancar) {

    public StorageProperties {
        endpoint = endpoint == null ? "http://localhost:19000" : endpoint;
        publicEndpoint = publicEndpoint == null ? endpoint : publicEndpoint;
        accessKey = accessKey == null ? "minioadmin" : accessKey;
        secretKey = secretKey == null ? "minioadmin" : secretKey;
        bucket = bucket == null ? "alumnos-documentos" : bucket;
        crearBucketAlArrancar = crearBucketAlArrancar == null || crearBucketAlArrancar;
    }
}
