package pe.edu.upeu.eduspace.pago.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.pago.config.PagoProperties;
import pe.edu.upeu.eduspace.pago.entity.Cuota;
import pe.edu.upeu.eduspace.pago.repository.CuotaRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MoraService {

    private final PagoProperties pagoProperties;
    private final CuotaRepository cuotaRepository;

    @Transactional
    public void aplicarMora(Cuota cuota, LocalDate fechaCalculo) {
        if ("PAGADA".equals(cuota.getEstado()) || "ANULADA".equals(cuota.getEstado())) {
            return;
        }

        long diasAtraso = ChronoUnit.DAYS.between(cuota.getFechaVencimiento(), fechaCalculo);
        if (diasAtraso <= pagoProperties.getMargenGraciaDias()) {
            return;
        }

        // Periodo de cálculo mensual proporcional
        BigDecimal porcentajeMensual = pagoProperties.getMoraPorcentajeMensual();
        BigDecimal factorDiario = porcentajeMensual.divide(BigDecimal.valueOf(100 * 30), 10, RoundingMode.HALF_UP);
        // Base SIN mora acumulada (si no, la mora se capitalizaría día a día)
        BigDecimal base = "SALDO".equalsIgnoreCase(pagoProperties.getMoraBase())
            ? cuota.getSaldo().subtract(cuota.getMora()).max(BigDecimal.ZERO)
            : cuota.getMontoBase().subtract(cuota.getDescuento()).max(BigDecimal.ZERO);
        BigDecimal moraCalculada = base.multiply(factorDiario).multiply(BigDecimal.valueOf(diasAtraso))
            .setScale(2, RoundingMode.HALF_UP);

        if (moraCalculada.compareTo(cuota.getMora()) > 0) {
            BigDecimal incremento = moraCalculada.subtract(cuota.getMora());
            cuota.setMora(moraCalculada);
            cuota.setMontoTotal(cuota.getMontoTotal().add(incremento));
            cuota.setSaldo(cuota.getSaldo().add(incremento));
            cuota.setActualizadaEn(LocalDateTime.now());
            cuotaRepository.save(cuota);
            log.info("Aplicada mora de S/ {} a la cuota ID {}", moraCalculada, cuota.getId());
        }
    }
}

