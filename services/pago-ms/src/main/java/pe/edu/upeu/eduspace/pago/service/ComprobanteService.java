package pe.edu.upeu.eduspace.pago.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.pago.dto.ComprobanteResponse;
import pe.edu.upeu.eduspace.pago.entity.Comprobante;
import pe.edu.upeu.eduspace.pago.entity.Pago;
import pe.edu.upeu.eduspace.pago.entity.SerieComprobante;
import pe.edu.upeu.eduspace.pago.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.pago.repository.ComprobanteRepository;
import pe.edu.upeu.eduspace.pago.repository.SerieComprobanteRepository;
import pe.edu.upeu.eduspace.pago.util.PdfTexto;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComprobanteService {

    private final ComprobanteRepository comprobanteRepository;
    private final SerieComprobanteRepository serieComprobanteRepository;
    private final MinioClient minioClient;

    @Value("${app.minio.bucket-comprobantes:comprobantes}")
    private String bucketComprobantes;

    @Transactional
    public Comprobante emitirComprobante(Pago pago, String tipo) {
        String tipoFinal = (tipo != null && !tipo.isBlank()) ? tipo : "RECIBO";

        // Incremento atómico bajo concurrencia con SELECT ... FOR UPDATE
        SerieComprobante serieComp = serieComprobanteRepository.findFirstByTipoActivaForUpdate(tipoFinal)
            .orElseThrow(() -> new IllegalStateException("No existe una serie activa para tipo: " + tipoFinal));

        long nuevoNumero = serieComp.getUltimoNumero() + 1;
        serieComp.setUltimoNumero(nuevoNumero);
        serieComprobanteRepository.save(serieComp);

        String serie = serieComp.getSerie();
        String rutaPdf = String.format("%s/%s-%08d.pdf", tipoFinal.toLowerCase(), serie, nuevoNumero);

        Comprobante comprobante = Comprobante.builder()
            .pago(pago)
            .tipo(tipoFinal)
            .serie(serie)
            .numero(nuevoNumero)
            .total(pago.getMontoTotal())
            .estado("EMITIDO")
            .rutaPdf(rutaPdf)
            .emitidoEn(LocalDateTime.now())
            .build();

        Comprobante guardado = comprobanteRepository.save(comprobante);

        // Guardar representación de comprobante en MinIO
        subirAComprobanteMinio(guardado, pago);

        return guardado;
    }

    private void subirAComprobanteMinio(Comprobante c, Pago p) {
        try {
            String contenido = String.format("""
                ====================================================
                              EDUSPACE - COMPROBANTE DE PAGO
                ====================================================
                Tipo: %s  | Serie-Número: %s-%08d
                Fecha: %s
                Alumno ID: %d
                Pagador: %s  (Doc: %s)
                Medio: %s   | Referencia: %s
                Monto Total: S/ %.2f
                ====================================================
                Estado: %s
                ====================================================
                """,
                c.getTipo(), c.getSerie(), c.getNumero(),
                c.getEmitidoEn(), p.getAlumnoId(),
                p.getPagadorNombre(), p.getPagadorDocumento(),
                p.getMedio(), p.getReferencia() != null ? p.getReferencia() : "-",
                c.getTotal(), c.getEstado());

            byte[] bytes = PdfTexto.generar("EDUSPACE - COMPROBANTE DE PAGO", contenido);
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketComprobantes)
                    .object(c.getRutaPdf())
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType("application/pdf")
                    .build()
            );
            log.info("Comprobante subido a MinIO en {}", c.getRutaPdf());
        } catch (Exception e) {
            log.warn("No se pudo subir comprobante a MinIO (se conserva registro en BD): {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Comprobante> buscarPorPago(Long pagoId) {
        return comprobanteRepository.findByPagoId(pagoId);
    }

    public ComprobanteResponse toResponse(Comprobante c) {
        return ComprobanteResponse.builder()
            .id(c.getId())
            .pagoId(c.getPago().getId())
            .tipo(c.getTipo())
            .serie(c.getSerie())
            .numero(c.getNumero())
            .numeroCompleto(String.format("%s-%08d", c.getSerie(), c.getNumero()))
            .total(c.getTotal())
            .estado(c.getEstado())
            .rutaPdf(c.getRutaPdf())
            .emitidoEn(c.getEmitidoEn())
            .build();
    }
}

