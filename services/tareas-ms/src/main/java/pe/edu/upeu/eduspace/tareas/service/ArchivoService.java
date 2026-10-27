package pe.edu.upeu.eduspace.tareas.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pe.edu.upeu.eduspace.tareas.config.TareasProperties;
import pe.edu.upeu.eduspace.tareas.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.tareas.exception.ServicioNoDisponibleException;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** URLs firmadas de MinIO. El tipo y tamaño se validan ANTES de firmar. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchivoService {

    private static final int EXPIRA_MIN = 15;

    private final MinioClient minio;
    private final TareasProperties props;

    @Value("${app.minio.bucket-entregas:tareas-entregas}")
    private String bucket;

    public void validar(String contentType, long tamanoBytes) {
        if (!props.getTiposArchivo().contains(contentType)) {
            throw new BusinessRuleException("Tipo de archivo no permitido: " + contentType);
        }
        long maxBytes = props.getArchivoMaxMb() * 1024L * 1024L;
        if (tamanoBytes <= 0 || tamanoBytes > maxBytes) {
            throw new BusinessRuleException("El archivo excede el tamaño máximo de " + props.getArchivoMaxMb() + " MB.");
        }
    }

    public String nuevaRuta(String prefijo, String nombre) {
        return prefijo + UUID.randomUUID() + "-" + sanear(nombre);
    }

    public String urlSubida(String ruta) {
        return firmar(Method.PUT, ruta);
    }

    public String urlDescarga(String ruta) {
        return firmar(Method.GET, ruta);
    }

    public int expiraMinutos() {
        return EXPIRA_MIN;
    }

    private String firmar(Method metodo, String ruta) {
        try {
            return minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(metodo).bucket(bucket).object(ruta).expiry(EXPIRA_MIN, TimeUnit.MINUTES).build());
        } catch (Exception e) {
            log.error("No se pudo firmar URL de MinIO: {}", e.getMessage());
            throw new ServicioNoDisponibleException("El almacenamiento de archivos no está disponible.");
        }
    }

    static String sanear(String nombre) {
        String base = nombre == null ? "archivo" : nombre.replaceAll(".*[\\\\/]", "");
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        return base.length() > 80 ? base.substring(base.length() - 80) : base;
    }
}
