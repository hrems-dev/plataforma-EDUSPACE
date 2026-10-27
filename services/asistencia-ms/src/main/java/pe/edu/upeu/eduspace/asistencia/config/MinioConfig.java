package pe.edu.upeu.eduspace.asistencia.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class MinioConfig {

    @Value("${app.minio.endpoint:http://localhost:19000}")
    private String endpoint;

    @Value("${app.minio.access-key:minioadmin}")
    private String accessKey;

    @Value("${app.minio.secret-key:minioadmin}")
    private String secretKey;

    @Value("${app.minio.bucket-justificaciones:justificaciones}")
    private String bucketJustificaciones;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build();

        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucketJustificaciones).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucketJustificaciones).build());
                log.info("Bucket creado en MinIO: {}", bucketJustificaciones);
            }
        } catch (Exception e) {
            log.warn("No se pudo verificar/crear el bucket {} en MinIO (puede ser creado externamente): {}", bucketJustificaciones, e.getMessage());
        }

        return client;
    }
}

