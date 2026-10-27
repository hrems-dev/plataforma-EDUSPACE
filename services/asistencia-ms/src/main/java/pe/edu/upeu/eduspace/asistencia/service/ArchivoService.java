package pe.edu.upeu.eduspace.asistencia.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pe.edu.upeu.eduspace.asistencia.config.AsistenciaProperties;
import pe.edu.upeu.eduspace.asistencia.dto.Dtos.ArchivoUrlResponse;
import pe.edu.upeu.eduspace.asistencia.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.asistencia.exception.ServicioNoDisponibleException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** URLs firmadas de MinIO para subir y descargar el sustento de una justificación. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchivoService {

    private static final int EXPIRA_MIN = 15;
    private static final Map<String, String> EXTENSION_POR_TIPO = Map.of(
        "application/pdf", "pdf", "image/jpeg", "jpg", "image/png", "png");

    private final MinioClient minio;
    private final AsistenciaProperties props;

    @Value("${app.minio.bucket-justificaciones:justificaciones}")
    private String bucket;

    /** Valida tipo antes de firmar y devuelve URL de subida (PUT) + la ruta a guardar. */
    public ArchivoUrlResponse urlSubida(Long justificacionId, String nombreArchivo, String contentType) {
        if (!props.getTiposArchivo().contains(contentType)) {
            throw new BusinessRuleException("Tipo de archivo no permitido: " + contentType + ". Permitidos: " + props.getTiposArchivo());
        }
        String ruta = "justificaciones/" + justificacionId + "/" + UUID.randomUUID() + "-" + sanear(nombreArchivo, contentType);
        try {
            String url = minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT).bucket(bucket).object(ruta).expiry(EXPIRA_MIN, TimeUnit.MINUTES).build());
            return new ArchivoUrlResponse(url, ruta, EXPIRA_MIN);
        } catch (Exception e) {
            log.error("No se pudo firmar URL de subida: {}", e.getMessage());
            throw new ServicioNoDisponibleException("El almacenamiento de archivos no está disponible.");
        }
    }

    public String urlDescarga(String ruta) {
        try {
            return minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET).bucket(bucket).object(ruta).expiry(EXPIRA_MIN, TimeUnit.MINUTES).build());
        } catch (Exception e) {
            log.error("No se pudo firmar URL de descarga: {}", e.getMessage());
            throw new ServicioNoDisponibleException("El almacenamiento de archivos no está disponible.");
        }
    }

    static String sanear(String nombre, String contentType) {
        String base = nombre == null ? "archivo" : nombre.replaceAll(".*[\\\\/]", "");
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.length() > 80) base = base.substring(base.length() - 80);
        String ext = EXTENSION_POR_TIPO.getOrDefault(contentType, "bin");
        return base.toLowerCase().endsWith("." + ext) ? base : base + "." + ext;
    }
}
