package pe.edu.upeu.eduspace.calificaciones.service;

import pe.edu.upeu.eduspace.calificaciones.util.PdfTexto;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.calificaciones.client.AlumnosClient;
import pe.edu.upeu.eduspace.calificaciones.client.PlanificacionClient;
import pe.edu.upeu.eduspace.calificaciones.dto.BoletinResponse;
import pe.edu.upeu.eduspace.calificaciones.dto.GenerarBoletinRequest;
import pe.edu.upeu.eduspace.calificaciones.entity.Boletin;
import pe.edu.upeu.eduspace.calificaciones.entity.CierrePeriodo;
import pe.edu.upeu.eduspace.calificaciones.entity.MatriculaLocal;
import pe.edu.upeu.eduspace.calificaciones.entity.PromedioPeriodo;
import pe.edu.upeu.eduspace.calificaciones.event.BoletinGeneradoEvent;
import pe.edu.upeu.eduspace.calificaciones.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.calificaciones.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.calificaciones.messaging.BoletinEventProducer;
import pe.edu.upeu.eduspace.calificaciones.repository.BoletinRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.CierrePeriodoRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.MatriculaLocalRepository;
import pe.edu.upeu.eduspace.calificaciones.repository.PromedioPeriodoRepository;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoletinService {

    private final BoletinRepository boletinRepository;
    private final MatriculaLocalRepository matriculaLocalRepository;
    private final PromedioPeriodoRepository promedioRepository;
    private final CierrePeriodoRepository cierrePeriodoRepository;
    private final BoletinEventProducer boletinEventProducer;
    private final PlanificacionClient planificacionClient;
    private final AlumnosClient alumnosClient;
    private final MinioClient minioClient;

    @Value("${app.minio.bucket-boletines:boletines}")
    private String bucketBoletines;

    @Transactional
    public List<BoletinResponse> generarBoletines(GenerarBoletinRequest request, Long usuarioId) {
        Long seccionId = request.getSeccionId();
        Long periodoId = request.getPeriodoId();

        // 1. Si es de periodo, validar que esté CERRADO
        if (periodoId != null) {
            CierrePeriodo cierre = cierrePeriodoRepository.findBySeccionIdAndPeriodoId(seccionId, periodoId)
                .orElseThrow(() -> new BusinessRuleException("El periodo debe estar CERRADO antes de emitir boletines"));

            if (!"CERRADO".equalsIgnoreCase(cierre.getEstado())) {
                throw new BusinessRuleException("No se pueden generar boletines: el periodo no está CERRADO");
            }
        }

        List<MatriculaLocal> matriculas;
        if (request.getAlumnoId() != null) {
            // un alumno puede tener matrículas de varios años: se elige la de la sección (o el año) pedida
            MatriculaLocal m = matriculaLocalRepository.findByAlumnoId(request.getAlumnoId())
                .stream()
                .filter(x -> seccionId == null || seccionId.equals(x.getSeccionId()))
                .filter(x -> request.getAnioId() == null || request.getAnioId().equals(x.getAnioId()))
                .max(java.util.Comparator.comparing(MatriculaLocal::getActualizadoEn))
                .orElseThrow(() -> new ResourceNotFoundException("Matrícula no encontrada para alumno " + request.getAlumnoId()));
            matriculas = List.of(m);
        } else {
            matriculas = matriculaLocalRepository.findBySeccionId(seccionId);
        }

        String periodoNombre = "Anual";
        if (periodoId != null) {
            try {
                Map<String, Object> pInfo = planificacionClient.getPeriodoPorId(periodoId);
                periodoNombre = pInfo != null && pInfo.get("nombre") != null ? pInfo.get("nombre").toString() : "Periodo " + periodoId;
            } catch (Exception e) {
                periodoNombre = "Periodo " + periodoId;
            }
        }

        List<BoletinResponse> responses = new ArrayList<>();

        for (MatriculaLocal m : matriculas) {
            // Versionado: anular boletín anterior vigente si existía
            int proximaVersion = 1;
            List<Boletin> anteriores = periodoId != null
                ? boletinRepository.findByMatriculaIdAndPeriodoIdOrderByVersionDesc(m.getMatriculaId(), periodoId)
                : boletinRepository.findByMatriculaIdAndPeriodoIdIsNullOrderByVersionDesc(m.getMatriculaId());

            if (!anteriores.isEmpty()) {
                proximaVersion = anteriores.get(0).getVersion() + 1;
                for (Boletin anterior : anteriores) {
                    if ("VIGENTE".equalsIgnoreCase(anterior.getEstado())) {
                        anterior.setEstado("ANULADO");
                        boletinRepository.save(anterior);
                    }
                }
            }

            String rutaPdf = String.format("boletines/%d/%s-v%d.pdf",
                m.getMatriculaId(),
                periodoId != null ? "periodo-" + periodoId : "anual",
                proximaVersion);

            Boletin boletin = Boletin.builder()
                .matriculaId(m.getMatriculaId())
                .alumnoId(m.getAlumnoId())
                .periodoId(periodoId)
                .version(proximaVersion)
                .estado("VIGENTE")
                .rutaPdf(rutaPdf)
                .generadoPor(usuarioId)
                .generadoEn(LocalDateTime.now())
                .build();

            Boletin guardado = boletinRepository.save(boletin);

            // Generar y subir archivo a MinIO
            subirBoletinMinio(guardado, m, periodoNombre);

            // Publicar evento en Kafka
            boletinEventProducer.publicarBoletinGenerado(BoletinGeneradoEvent.builder()
                .boletinId(guardado.getId())
                .alumnoId(guardado.getAlumnoId())
                .matriculaId(guardado.getMatriculaId())
                .periodoId(guardado.getPeriodoId())
                .periodoNombre(periodoNombre)
                .build());

            responses.add(toResponse(guardado));
        }

        return responses;
    }

    private void subirBoletinMinio(Boletin b, MatriculaLocal m, String periodoNombre) {
        try {
            List<PromedioPeriodo> promedios = b.getPeriodoId() != null
                ? promedioRepository.findByMatriculaIdAndPeriodoId(m.getMatriculaId(), b.getPeriodoId())
                : promedioRepository.findByMatriculaId(m.getMatriculaId());

            StringBuilder sb = new StringBuilder();
            sb.append("=================================================================\n");
            sb.append("                  EDUSPACE - BOLETÍN DE NOTAS OFICIAL            \n");
            sb.append("=================================================================\n");
            sb.append(String.format("Alumno: %s (ID: %d)\n", m.getAlumnoNombre(), m.getAlumnoId()));
            sb.append(String.format("Matrícula: %d | Sección ID: %d | Periodo: %s\n", m.getMatriculaId(), m.getSeccionId(), periodoNombre));
            sb.append(String.format("Versión: %d | Estado: %s | Emitido: %s\n", b.getVersion(), b.getEstado(), b.getGeneradoEn()));
            sb.append("-----------------------------------------------------------------\n");
            sb.append(String.format("%-10s %-25s %-10s %-8s\n", "CURSO ID", "ASIGNATURA", "PROMEDIO", "LITERAL"));
            sb.append("-----------------------------------------------------------------\n");

            for (PromedioPeriodo p : promedios) {
                sb.append(String.format("%-10d %-25s %-10.2f %-8s\n",
                    p.getCursoId(), "Curso " + p.getCursoId(), p.getPromedio(), p.getLiteral() != null ? p.getLiteral() : "-"));
            }
            sb.append("=================================================================\n");
            sb.append("Documento oficial firmado digitalmente por EduSpace Calificaciones\n");

            byte[] bytes = PdfTexto.generar("EDUSPACE - BOLETIN DE NOTAS", sb.toString());
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketBoletines)
                    .object(b.getRutaPdf())
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType("application/pdf")
                    .build()
            );
            log.info("Boletín subido a MinIO en {}", b.getRutaPdf());
        } catch (Exception e) {
            log.warn("Error al subir boletín a MinIO: {}", e.getMessage());
        }
    }

    public String obtenerUrlDescarga(Long boletinId) {
        Boletin b = boletinRepository.findById(boletinId)
            .orElseThrow(() -> new ResourceNotFoundException("Boletín no encontrado: " + boletinId));

        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketBoletines)
                    .object(b.getRutaPdf())
                    .expiry(15, TimeUnit.MINUTES)
                    .build()
            );
        } catch (Exception e) {
            log.warn("No se pudo generar URL firmada de MinIO: {}", e.getMessage());
            return "/api/v1/calificaciones/boletines/" + boletinId + "/descargar-directa";
        }
    }

    @Transactional(readOnly = true)
    public List<BoletinResponse> listarBoletinesPorAlumno(Long alumnoId) {
        return boletinRepository.findByAlumnoIdOrderByGeneradoEnDesc(alumnoId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public BoletinResponse obtenerPorId(Long id) {
        Boletin b = boletinRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Boletín no encontrado con ID: " + id));
        return toResponse(b);
    }

    public BoletinResponse toResponse(Boletin b) {
        return BoletinResponse.builder()
            .id(b.getId())
            .matriculaId(b.getMatriculaId())
            .alumnoId(b.getAlumnoId())
            .periodoId(b.getPeriodoId())
            .version(b.getVersion())
            .estado(b.getEstado())
            .rutaPdf(b.getRutaPdf())
            .urlDescarga("/api/v1/calificaciones/boletines/" + b.getId() + "/url")
            .generadoPor(b.getGeneradoPor())
            .generadoEn(b.getGeneradoEn())
            .build();
    }
}

