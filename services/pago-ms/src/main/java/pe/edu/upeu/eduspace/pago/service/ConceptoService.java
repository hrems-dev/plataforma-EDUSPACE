package pe.edu.upeu.eduspace.pago.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.pago.dto.ConceptoRequest;
import pe.edu.upeu.eduspace.pago.dto.ConceptoResponse;
import pe.edu.upeu.eduspace.pago.entity.ConceptoPago;
import pe.edu.upeu.eduspace.pago.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.pago.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.pago.repository.ConceptoPagoRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConceptoService {

    private final ConceptoPagoRepository conceptoPagoRepository;

    @Transactional(readOnly = true)
    public List<ConceptoResponse> listarActivos() {
        return conceptoPagoRepository.findByActivoTrue().stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ConceptoResponse obtenerPorId(Long id) {
        ConceptoPago c = conceptoPagoRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Concepto de pago no encontrado con ID: " + id));
        return toResponse(c);
    }

    @Transactional
    public ConceptoResponse crear(ConceptoRequest req) {
        if (conceptoPagoRepository.findByCodigo(req.getCodigo()).isPresent()) {
            throw new BusinessRuleException("Ya existe un concepto de pago con el código: " + req.getCodigo());
        }
        ConceptoPago c = ConceptoPago.builder()
            .codigo(req.getCodigo().toUpperCase())
            .nombre(req.getNombre())
            .tipo(req.getTipo())
            .activo(req.getActivo() != null ? req.getActivo() : true)
            .build();
        return toResponse(conceptoPagoRepository.save(c));
    }

    @Transactional
    public ConceptoResponse actualizar(Long id, ConceptoRequest req) {
        ConceptoPago c = conceptoPagoRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Concepto de pago no encontrado con ID: " + id));
        c.setNombre(req.getNombre());
        c.setTipo(req.getTipo());
        if (req.getActivo() != null) {
            c.setActivo(req.getActivo());
        }
        return toResponse(conceptoPagoRepository.save(c));
    }

    private ConceptoResponse toResponse(ConceptoPago c) {
        return ConceptoResponse.builder()
            .id(c.getId())
            .codigo(c.getCodigo())
            .nombre(c.getNombre())
            .tipo(c.getTipo())
            .activo(c.getActivo())
            .build();
    }
}

