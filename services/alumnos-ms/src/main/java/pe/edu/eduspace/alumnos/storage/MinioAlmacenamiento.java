package pe.edu.eduspace.alumnos.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.edu.eduspace.alumnos.config.StorageProperties;
import pe.edu.eduspace.alumnos.exception.StorageException;

/**
 * Implementación sobre MinIO. Usa dos clientes: el interno (host de Docker) para subir y borrar, y uno con el
 * host público solo para firmar URL de descarga, porque la firma incluye el host y el navegador no resuelve
 * el nombre interno.
 */
@Component
public class MinioAlmacenamiento implements AlmacenamientoArchivos {

    private static final Logger log = LoggerFactory.getLogger(MinioAlmacenamiento.class);
    private static final String REGION = "us-east-1";

    private final StorageProperties props;
    private final MinioClient interno;
    private final MinioClient publico;
    private volatile boolean bucketListo;

    public MinioAlmacenamiento(StorageProperties props) {
        this.props = props;
        this.interno = MinioClient.builder().endpoint(props.endpoint()).credentials(props.accessKey(), props.secretKey())
                .region(REGION).build();
        this.publico = MinioClient.builder().endpoint(props.publicEndpoint()).credentials(props.accessKey(), props.secretKey())
                .region(REGION).build();
    }

    /** Crea el bucket al arrancar; si MinIO aún no responde, se reintenta en la primera subida. */
    @PostConstruct
    void inicializar() {
        if (!props.crearBucketAlArrancar()) {
            return;
        }
        try {
            asegurarBucket();
        } catch (StorageException ex) {
            log.warn("component=MinioAlmacenamiento eventType=BucketInit status=PENDIENTE bucket={} cause={}",
                    props.bucket(), ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
        }
    }

    @Override
    public void guardar(String clave, InputStream contenido, long tamano, String contentType) {
        try {
            asegurarBucket();
            interno.putObject(PutObjectArgs.builder().bucket(props.bucket()).object(clave)
                    .stream(contenido, tamano, -1).contentType(contentType).build());
        } catch (StorageException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new StorageException("No se pudo guardar el archivo " + clave, ex);
        }
    }

    @Override
    public String urlDescarga(String clave, Duration vigencia) {
        try {
            return publico.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET)
                    .bucket(props.bucket()).object(clave).expiry((int) vigencia.toSeconds(), TimeUnit.SECONDS).build());
        } catch (Exception ex) {
            throw new StorageException("No se pudo firmar la descarga de " + clave, ex);
        }
    }

    @Override
    public void eliminar(String clave) {
        try {
            interno.removeObject(RemoveObjectArgs.builder().bucket(props.bucket()).object(clave).build());
        } catch (Exception ex) {
            throw new StorageException("No se pudo eliminar el archivo " + clave, ex);
        }
    }

    private void asegurarBucket() {
        if (bucketListo) {
            return;
        }
        try {
            if (!interno.bucketExists(BucketExistsArgs.builder().bucket(props.bucket()).build())) {
                interno.makeBucket(MakeBucketArgs.builder().bucket(props.bucket()).region(REGION).build());
                log.info("component=MinioAlmacenamiento eventType=BucketCreado status=OK bucket={}", props.bucket());
            }
            bucketListo = true;
        } catch (Exception ex) {
            throw new StorageException("No se pudo verificar el bucket " + props.bucket(), ex);
        }
    }
}
