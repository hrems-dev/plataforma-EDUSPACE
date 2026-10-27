package pe.edu.upeu.eduspace.pago.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.pago.dto.TarifaRequest;
import pe.edu.upeu.eduspace.pago.dto.TarifaResponse;
import pe.edu.upeu.eduspace.pago.entity.ConceptoPago;
import pe.edu.upeu.eduspace.pago.entity.Tarifa;
import pe.edu.upeu.eduspace.pago.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.pago.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.pago.repository.ConceptoPagoRepository;
import pe.edu.upeu.eduspace.pago.repository.CuotaRepository;
import pe.edu.upeu.eduspace.pago.repository.TarifaRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TarifaService {

    private final TarifaRepository tarifaRepository;
    private final ConceptoPagoRepository conceptoPagoRepository;
    private final CuotaRepository cuotaRepository;
    private final GeneradorCuotasService generadorCuotasService;

    @Transactional(readOnly = true)
    public List<TarifaResponse> listarPorAnio(Long anioId) {
        return tarifaRepository.findByAnioIdAndActivoTrue(anioId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public TarifaResponse obtenerPorId(Long id) {
        Tarifa t = tarifaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Tarifa no encontrada con ID: " + id));
        return toResponse(t);
    }

    @Transactional
    public TarifaResponse crear(TarifaRequest req) {
        ConceptoPago concepto = conceptoPagoRepository.findById(req.getConceptoId())
            .orElseThrow(() -> new ResourceNotFoundException("Concepto no encontrado con ID: " + req.getConceptoId()));

        Tarifa t = Tarifa.builder()
            .anioId(req.getAnioId())
            .nivelId(req.getNivelId())
            .gradoId(req.getGradoId())
            .concepto(concepto)
            .monto(req.getMonto())
            .numCuotas(req.getNumCuotas())
            .mesInicio(req.getMesInicio())
            .diaVencimiento(req.getDiaVencimiento())
            .activo(req.getActivo() != null ? req.getActivo() : true)
            .build();

        Tarifa guardada = tarifaRepository.save(t);
        generadorCuotasService.reintentarPorTarifa(guardada);
        return toResponse(guardada);
    }

    @Transactional
    public TarifaResponse actualizar(Long id, TarifaRequest req) {
        Tarifa t = tarifaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Tarifa no encontrada con ID: " + id));

        // Regla: no se modifica si ya generó cuotas asociadas
        // (En ese caso solo se permite desactivar o se lanza error si cambian montos/fechas)
        if (t.getMonto().compareTo(req.getMonto()) != 0) {   // compareTo: 150 y 150.00 son el mismo monto
            // Se valida si hay cuotas que referencian esta tarifa
            // Si hay cuotas, solo se permite desactivar o cambiar estado
            t.setActivo(req.getActivo());
            throw new BusinessRuleException("No se puede modificar el monto de una tarifa ya existente: desactívela y cree una nueva.");
        }

        t.setActivo(req.getActivo());
        return toResponse(tarifaRepository.save(t));
    }

    private TarifaResponse toResponse(Tarifa t) {
        return TarifaResponse.builder()
            .id(t.getId())
            .anioId(t.getAnioId())
            .nivelId(t.getNivelId())
            .gradoId(t.getGradoId())
            .conceptoId(t.getConcepto().getId())
            .conceptoCodigo(t.getConcepto().getCodigo())
            .conceptoNombre(t.getConcepto().getNombre())
            .monto(t.getMonto())
            .numCuotas(t.getNumCuotas())
            .mesInicio(t.getMesInicio())
            .diaVencimiento(t.getDiaVencimiento())
            .activo(t.getActivo())
            .build();
    }
}

