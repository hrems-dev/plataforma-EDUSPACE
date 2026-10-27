package pe.edu.upeu.eduspace.pago.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.pago.client.PlanificacionClient;
import pe.edu.upeu.eduspace.pago.config.PagoProperties;
import pe.edu.upeu.eduspace.pago.entity.*;
import pe.edu.upeu.eduspace.pago.event.MatriculaEvent;
import pe.edu.upeu.eduspace.pago.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeneradorCuotasService {

    private final ConceptoPagoRepository conceptoPagoRepository;
    private final TarifaRepository tarifaRepository;
    private final DescuentoAlumnoRepository descuentoAlumnoRepository;
    private final CuotaRepository cuotaRepository;
    private final MatriculaLocalRepository matriculaLocalRepository;
    private final PagoProperties pagoProperties;
    private final AlertaAdminRepository alertaRepository;
    private final PlanificacionClient planificacionClient;

    @Transactional
    public void generarCuotaMatricula(MatriculaEvent event) {
        log.info("Generando cuota de matrícula para matrícula: {}", event.getMatriculaId());

        ConceptoPago conceptoMatricula = conceptoPagoRepository.findByCodigo("MATRICULA")
            .orElseThrow(() -> new IllegalStateException("Concepto MATRICULA no configurado en BD"));

        // Evitar duplicados por idempotencia (uk_cuota)
        if (cuotaRepository.findByMatriculaMatriculaIdAndConceptoIdAndNumeroCuota(event.getMatriculaId(), conceptoMatricula.getId(), (short) 1).isPresent()) {
            log.info("Cuota de matrícula ya existe para matrícula {}", event.getMatriculaId());
            return;
        }

        MatriculaLocal matricula = matriculaLocalRepository.findById(event.getMatriculaId())
            .orElseThrow(() -> new IllegalStateException("Matrícula local no encontrada: " + event.getMatriculaId()));

        Optional<Tarifa> optTarifa = tarifaRepository.findTarifaAplicable(
            event.getAnioId(), event.getNivelId(), event.getGradoId(), conceptoMatricula.getId()
        );

        if (optTarifa.isEmpty()) {
            alertarSinTarifa("MATRICULA", event);
            return;
        }

        Tarifa tarifa = optTarifa.get();
        BigDecimal montoBase = tarifa.getMonto();
        BigDecimal descuento = calcularDescuento(event.getAlumnoId(), event.getAnioId(), "MATRICULA", montoBase);
        BigDecimal total = montoBase.subtract(descuento).max(BigDecimal.ZERO);

        LocalDate fechaVencimiento = LocalDate.now().plusDays(pagoProperties.getDiasVigenciaPrematricula());

        Cuota cuota = Cuota.builder()
            .matricula(matricula)
            .alumnoId(event.getAlumnoId())
            .anioId(event.getAnioId())
            .concepto(conceptoMatricula)
            .tarifa(tarifa)
            .descripcion("Matrícula escolar " + anioCalendario(event.getAnioId()))
            .numeroCuota((short) 1)
            .montoBase(montoBase)
            .descuento(descuento)
            .mora(BigDecimal.ZERO)
            .montoTotal(total)
            .saldo(total)
            .fechaVencimiento(fechaVencimiento)
            .estado(total.compareTo(BigDecimal.ZERO) == 0 ? "PAGADA" : "PENDIENTE")
            .avisoEnviado(false)
            .creadaEn(LocalDateTime.now())
            .actualizadaEn(LocalDateTime.now())
            .build();

        cuotaRepository.save(cuota);
        log.info("Cuota de matrícula generada exitosamente con ID: {}, saldo: {}", cuota.getId(), cuota.getSaldo());
    }

    @Transactional
    public void generarCuotasPension(MatriculaEvent event) {
        log.info("Generando cuotas de pensión para matrícula confirmada: {}", event.getMatriculaId());

        ConceptoPago conceptoPension = conceptoPagoRepository.findByCodigo("PENSION")
            .orElseThrow(() -> new IllegalStateException("Concepto PENSION no configurado en BD"));

        MatriculaLocal matricula = matriculaLocalRepository.findById(event.getMatriculaId())
            .orElseThrow(() -> new IllegalStateException("Matrícula local no encontrada: " + event.getMatriculaId()));

        Optional<Tarifa> optTarifa = tarifaRepository.findTarifaAplicable(
            event.getAnioId(), event.getNivelId(), event.getGradoId(), conceptoPension.getId()
        );

        if (optTarifa.isEmpty()) {
            alertarSinTarifa("PENSION", event);
            return;
        }

        Tarifa tarifa = optTarifa.get();
        short totalCuotas = tarifa.getNumCuotas() != null ? tarifa.getNumCuotas() : 10;
        short mesInicio = tarifa.getMesInicio() != null ? tarifa.getMesInicio() : 3;
        short diaVenc = tarifa.getDiaVencimiento() != null ? tarifa.getDiaVencimiento() : 5;
        // El monto de la tarifa es el TOTAL de la pensión del año; se reparte en num_cuotas con
        // redondeo a 2 decimales y la diferencia se carga a la última cuota.
        BigDecimal montoCuotaBase = tarifa.getMonto().divide(BigDecimal.valueOf(totalCuotas), 2, RoundingMode.DOWN);
        BigDecimal acumulado = BigDecimal.ZERO;

        int anio = anioCalendario(event.getAnioId());

        for (short i = 1; i <= totalCuotas; i++) {
            final short numCuota = i;
            BigDecimal montoBasePorCuota = i == totalCuotas ? tarifa.getMonto().subtract(acumulado) : montoCuotaBase;
            acumulado = acumulado.add(montoBasePorCuota);
            if (cuotaRepository.findByMatriculaMatriculaIdAndConceptoIdAndNumeroCuota(event.getMatriculaId(), conceptoPension.getId(), numCuota).isPresent()) {
                continue; // ya generada
            }

            int mes = mesInicio + (i - 1);
            int anioCuota = anio;
            if (mes > 12) {
                mes -= 12;
                anioCuota += 1;
            }

            LocalDate fechaVenc = LocalDate.of(anioCuota, mes, Math.min(diaVenc, 28));
            BigDecimal descCuota = calcularDescuento(event.getAlumnoId(), event.getAnioId(), "PENSION", montoBasePorCuota);
            BigDecimal totalCuota = montoBasePorCuota.subtract(descCuota).max(BigDecimal.ZERO);

            Cuota cuota = Cuota.builder()
                .matricula(matricula)
                .alumnoId(event.getAlumnoId())
                .anioId(event.getAnioId())
                .concepto(conceptoPension)
                .tarifa(tarifa)
                .descripcion(String.format("Pensión %02d/%d", mes, anioCuota))
                .numeroCuota(numCuota)
                .montoBase(montoBasePorCuota)
                .descuento(descCuota)
                .mora(BigDecimal.ZERO)
                .montoTotal(totalCuota)
                .saldo(totalCuota)
                .fechaVencimiento(fechaVenc)
                .estado(totalCuota.compareTo(BigDecimal.ZERO) == 0 ? "PAGADA" : "PENDIENTE")
                .avisoEnviado(false)
                .creadaEn(LocalDateTime.now())
                .actualizadaEn(LocalDateTime.now())
                .build();

            cuotaRepository.save(cuota);
        }
        log.info("Generadas {} cuotas de pensión para matrícula {}", totalCuotas, event.getMatriculaId());
    }

    @Transactional
    public void anularCuotasPorRetiroOAnulacion(Long matriculaId) {
        log.info("Anulando cuotas pendientes para matrícula {}", matriculaId);
        List<Cuota> cuotas = cuotaRepository.findByMatriculaMatriculaId(matriculaId);
        for (Cuota c : cuotas) {
            if ("PENDIENTE".equals(c.getEstado()) || "VENCIDA".equals(c.getEstado())) {
                c.setEstado("ANULADA");
                c.setSaldo(BigDecimal.ZERO);
                c.setActualizadaEn(LocalDateTime.now());
                cuotaRepository.save(c);
            } else if ("PARCIAL".equals(c.getEstado())
                    && !alertaRepository.existsByTipoAndMatriculaIdAndResueltaFalse("CUOTA_PARCIAL", matriculaId)) {
                // Con pagos aplicados: la administración decide (devolución / condonación)
                alertaRepository.save(AlertaAdmin.builder()
                    .tipo("CUOTA_PARCIAL").matriculaId(matriculaId).anioId(c.getAnioId())
                    .detalle("Matrícula " + matriculaId + " retirada/anulada con cuota(s) pagadas parcialmente; requiere decisión.")
                    .build());
                log.warn("ALERTA ADMINISTRATIVA: cuota parcial en matrícula {} retirada/anulada", matriculaId);
            }
        }
    }

    /** Año calendario del año académico; si planificación no responde, usa el año actual. */
    int anioCalendario(Long anioId) {
        try {
            PlanificacionClient.AnioInterno a = planificacionClient.getAnio(anioId);
            if (a != null && a.fechaInicio() != null) {
                return a.fechaInicio().getYear();
            }
            if (a != null && a.nombre() != null && a.nombre().matches("\\d{4}")) {
                return Integer.parseInt(a.nombre());
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener el año académico {}: {}", anioId, e.getMessage());
        }
        return LocalDate.now().getYear();
    }

    private void alertarSinTarifa(String concepto, MatriculaEvent event) {
        log.error("ALERTA ADMINISTRATIVA: No existe tarifa de {} para año={}, nivel={}, grado={} (matrícula {})",
            concepto, event.getAnioId(), event.getNivelId(), event.getGradoId(), event.getMatriculaId());
        if (!alertaRepository.existsByTipoAndMatriculaIdAndResueltaFalse("SIN_TARIFA", event.getMatriculaId())) {
            alertaRepository.save(AlertaAdmin.builder()
                .tipo("SIN_TARIFA").matriculaId(event.getMatriculaId())
                .anioId(event.getAnioId()).nivelId(event.getNivelId()).gradoId(event.getGradoId())
                .detalle("Falta tarifa de " + concepto + " (año " + event.getAnioId() + ", nivel " + event.getNivelId()
                    + ", grado " + event.getGradoId() + "); no se generaron cuotas de la matrícula " + event.getMatriculaId())
                .build());
        }
    }

    /** Al crear una tarifa: genera las cuotas de las matrículas que quedaron sin ellas y resuelve las alertas. */
    @Transactional
    public void reintentarPorTarifa(Tarifa tarifa) {
        for (AlertaAdmin a : alertaRepository.findByTipoAndAnioIdAndNivelIdAndResueltaFalse(
                "SIN_TARIFA", tarifa.getAnioId(), tarifa.getNivelId())) {
            if (tarifa.getGradoId() != null && !tarifa.getGradoId().equals(a.getGradoId())) continue;
            matriculaLocalRepository.findById(a.getMatriculaId()).ifPresent(m -> {
                MatriculaEvent ev = MatriculaEvent.builder()
                    .matriculaId(m.getMatriculaId()).alumnoId(m.getAlumnoId()).alumnoNombre(m.getAlumnoNombre())
                    .anioId(m.getAnioId()).nivelId(m.getNivelId()).gradoId(m.getGradoId()).seccionId(m.getSeccionId())
                    .estado(m.getEstado()).build();
                String tipo = tarifa.getConcepto().getTipo();
                if ("MATRICULA".equals(tipo)) generarCuotaMatricula(ev);
                else if ("PENSION".equals(tipo) && "MATRICULADO".equals(m.getEstado())) generarCuotasPension(ev);
            });
            a.setResuelta(true);
            a.setResueltaEn(LocalDateTime.now());
            alertaRepository.save(a);
        }
    }

    private BigDecimal calcularDescuento(Long alumnoId, Long anioId, String tipoAplica, BigDecimal montoBase) {
        List<DescuentoAlumno> descuentos = descuentoAlumnoRepository.findDescuentosVigentesConDetalle(alumnoId, anioId);
        BigDecimal totalDescuento = BigDecimal.ZERO;

        for (DescuentoAlumno da : descuentos) {
            Descuento d = da.getDescuento();
            if ("TODO".equals(d.getAplicaA()) || tipoAplica.equals(d.getAplicaA())) {
                if ("PORCENTAJE".equals(d.getTipo())) {
                    BigDecimal desc = montoBase.multiply(d.getValor()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    totalDescuento = totalDescuento.add(desc);
                } else if ("MONTO_FIJO".equals(d.getTipo())) {
                    totalDescuento = totalDescuento.add(d.getValor());
                }
            }
        }
        return totalDescuento.min(montoBase);
    }
}

