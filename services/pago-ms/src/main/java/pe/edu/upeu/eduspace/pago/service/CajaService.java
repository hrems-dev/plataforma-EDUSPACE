package pe.edu.upeu.eduspace.pago.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.pago.dto.*;
import pe.edu.upeu.eduspace.pago.entity.CajaSesion;
import pe.edu.upeu.eduspace.pago.entity.Pago;
import pe.edu.upeu.eduspace.pago.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.pago.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.pago.repository.CajaSesionRepository;
import pe.edu.upeu.eduspace.pago.repository.PagoRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CajaService {

    private final CajaSesionRepository cajaSesionRepository;
    private final PagoRepository pagoRepository;

    @Transactional
    public CajaResponse abrirCaja(Long cajeroUsuarioId, AbrirCajaRequest req) {
        if (cajaSesionRepository.existsCajaAbierta(cajeroUsuarioId)) {
            throw new BusinessRuleException("El cajero ya tiene una sesión de caja ABIERTA actualmente.");
        }

        CajaSesion sesion = CajaSesion.builder()
            .cajeroUsuarioId(cajeroUsuarioId)
            .aperturaEn(LocalDateTime.now())
            .montoApertura(req.getMontoApertura() != null ? req.getMontoApertura() : BigDecimal.ZERO)
            .estado("ABIERTA")
            .build();

        CajaSesion guardada = cajaSesionRepository.save(sesion);
        log.info("Caja abierta ID: {} por cajero: {}", guardada.getId(), cajeroUsuarioId);
        return toResponse(guardada);
    }

    @Transactional
    public CierreCajaResponse cerrarCaja(Long cajeroUsuarioId, CerrarCajaRequest req) {
        CajaSesion sesion = cajaSesionRepository.findFirstByCajeroUsuarioIdAndEstado(cajeroUsuarioId, "ABIERTA")
            .orElseThrow(() -> new BusinessRuleException("No tiene ninguna caja ABIERTA para cerrar."));

        List<Pago> pagos = pagoRepository.findByCajaSesionId(sesion.getId());

        BigDecimal totalEfectivo = BigDecimal.ZERO;
        BigDecimal totalOtros = BigDecimal.ZERO;
        Map<String, BigDecimal> totalesPorMedio = new HashMap<>();

        for (Pago p : pagos) {
            if ("CONFIRMADO".equals(p.getEstado())) {
                totalesPorMedio.merge(p.getMedio(), p.getMontoTotal(), BigDecimal::add);
                if ("EFECTIVO".equals(p.getMedio())) {
                    totalEfectivo = totalEfectivo.add(p.getMontoTotal());
                } else {
                    totalOtros = totalOtros.add(p.getMontoTotal());
                }
            }
        }

        BigDecimal totalCobrado = totalEfectivo.add(totalOtros);
        BigDecimal efectivoEsperado = sesion.getMontoApertura().add(totalEfectivo);
        BigDecimal declarado = req.getMontoCierreDeclarado();
        BigDecimal diferencia = declarado.subtract(efectivoEsperado);

        sesion.setCierreEn(LocalDateTime.now());
        sesion.setMontoCierreDeclarado(declarado);
        sesion.setMontoCierreSistema(efectivoEsperado);
        sesion.setDiferencia(diferencia);
        sesion.setEstado("CERRADA");

        cajaSesionRepository.save(sesion);
        log.info("Caja cerrada ID: {}. Efectivo esperado: {}, Declarado: {}, Diferencia: {}",
            sesion.getId(), efectivoEsperado, declarado, diferencia);

        return CierreCajaResponse.builder()
            .cajaId(sesion.getId())
            .cajeroUsuarioId(cajeroUsuarioId)
            .aperturaEn(sesion.getAperturaEn())
            .cierreEn(sesion.getCierreEn())
            .montoApertura(sesion.getMontoApertura())
            .cobrosEfectivo(totalEfectivo)
            .cobrosOtrosMedios(totalOtros)
            .totalCobrado(totalCobrado)
            .efectivoEsperado(efectivoEsperado)
            .montoDeclarado(declarado)
            .diferencia(diferencia)
            .totalesPorMedio(totalesPorMedio)
            .build();
    }

    @Transactional(readOnly = true)
    public CajaResponse obtenerCajaActual(Long cajeroUsuarioId) {
        return cajaSesionRepository.findFirstByCajeroUsuarioIdAndEstado(cajeroUsuarioId, "ABIERTA")
            .map(this::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("No tiene ninguna caja abierta actualmente."));
    }

    @Transactional(readOnly = true)
    public CierreCajaResponse reporteCaja(Long cajaId) {
        CajaSesion sesion = cajaSesionRepository.findById(cajaId)
            .orElseThrow(() -> new ResourceNotFoundException("Sesión de caja no encontrada: " + cajaId));

        List<Pago> pagos = pagoRepository.findByCajaSesionId(sesion.getId());
        BigDecimal totalEfectivo = BigDecimal.ZERO;
        BigDecimal totalOtros = BigDecimal.ZERO;
        Map<String, BigDecimal> totalesPorMedio = new HashMap<>();

        for (Pago p : pagos) {
            if ("CONFIRMADO".equals(p.getEstado())) {
                totalesPorMedio.merge(p.getMedio(), p.getMontoTotal(), BigDecimal::add);
                if ("EFECTIVO".equals(p.getMedio())) {
                    totalEfectivo = totalEfectivo.add(p.getMontoTotal());
                } else {
                    totalOtros = totalOtros.add(p.getMontoTotal());
                }
            }
        }

        return CierreCajaResponse.builder()
            .cajaId(sesion.getId())
            .cajeroUsuarioId(sesion.getCajeroUsuarioId())
            .aperturaEn(sesion.getAperturaEn())
            .cierreEn(sesion.getCierreEn())
            .montoApertura(sesion.getMontoApertura())
            .cobrosEfectivo(totalEfectivo)
            .cobrosOtrosMedios(totalOtros)
            .totalCobrado(totalEfectivo.add(totalOtros))
            .efectivoEsperado(sesion.getMontoCierreSistema())
            .montoDeclarado(sesion.getMontoCierreDeclarado())
            .diferencia(sesion.getDiferencia())
            .totalesPorMedio(totalesPorMedio)
            .build();
    }

    private CajaResponse toResponse(CajaSesion c) {
        return CajaResponse.builder()
            .id(c.getId())
            .cajeroUsuarioId(c.getCajeroUsuarioId())
            .aperturaEn(c.getAperturaEn())
            .montoApertura(c.getMontoApertura())
            .cierreEn(c.getCierreEn())
            .montoCierreDeclarado(c.getMontoCierreDeclarado())
            .montoCierreSistema(c.getMontoCierreSistema())
            .diferencia(c.getDiferencia())
            .estado(c.getEstado())
            .build();
    }
}

