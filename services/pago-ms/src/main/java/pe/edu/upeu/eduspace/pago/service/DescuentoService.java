package pe.edu.upeu.eduspace.pago.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.pago.dto.AsignarDescuentoRequest;
import pe.edu.upeu.eduspace.pago.dto.DescuentoRequest;
import pe.edu.upeu.eduspace.pago.dto.DescuentoResponse;
import pe.edu.upeu.eduspace.pago.entity.Descuento;
import pe.edu.upeu.eduspace.pago.entity.DescuentoAlumno;
import pe.edu.upeu.eduspace.pago.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.pago.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.pago.repository.DescuentoAlumnoRepository;
import pe.edu.upeu.eduspace.pago.repository.DescuentoRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DescuentoService {

    private final DescuentoRepository descuentoRepository;
    private final DescuentoAlumnoRepository descuentoAlumnoRepository;

    @Transactional(readOnly = true)
    public List<DescuentoResponse> listarActivos() {
        return descuentoRepository.findByActivoTrue().stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public DescuentoResponse crear(DescuentoRequest req) {
        Descuento d = Descuento.builder()
            .nombre(req.getNombre())
            .tipo(req.getTipo())
            .valor(req.getValor())
            .aplicaA(req.getAplicaA())
            .activo(req.getActivo() != null ? req.getActivo() : true)
            .build();
        return toResponse(descuentoRepository.save(d));
    }

    @Transactional
    public DescuentoResponse actualizar(Long id, DescuentoRequest req) {
        Descuento d = descuentoRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Descuento no encontrado: " + id));
        d.setNombre(req.getNombre());
        d.setTipo(req.getTipo());
        d.setValor(req.getValor());
        d.setAplicaA(req.getAplicaA());
        if (req.getActivo() != null) d.setActivo(req.getActivo());
        return toResponse(descuentoRepository.save(d));
    }

    @Transactional
    public void asignarDescuentoAlumno(AsignarDescuentoRequest req, Long usuarioId) {
        Descuento d = descuentoRepository.findById(req.getDescuentoId())
            .orElseThrow(() -> new ResourceNotFoundException("Descuento no encontrado: " + req.getDescuentoId()));

        descuentoAlumnoRepository.findByAlumnoIdAndDescuentoIdAndAnioId(req.getAlumnoId(), req.getDescuentoId(), req.getAnioId())
            .ifPresent(da -> {
                throw new BusinessRuleException("El alumno ya tiene asignado este descuento para el año " + req.getAnioId());
            });

        DescuentoAlumno da = DescuentoAlumno.builder()
            .alumnoId(req.getAlumnoId())
            .descuento(d)
            .anioId(req.getAnioId())
            .motivo(req.getMotivo())
            .aprobadoPor(usuarioId)
            .vigente(true)
            .build();

        descuentoAlumnoRepository.save(da);
    }

    private DescuentoResponse toResponse(Descuento d) {
        return DescuentoResponse.builder()
            .id(d.getId())
            .nombre(d.getNombre())
            .tipo(d.getTipo())
            .valor(d.getValor())
            .aplicaA(d.getAplicaA())
            .activo(d.getActivo())
            .build();
    }
}

