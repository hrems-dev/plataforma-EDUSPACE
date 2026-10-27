package pe.edu.upeu.eduspace.matricula.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.matricula.dto.Dtos.RequisitoRequest;
import pe.edu.upeu.eduspace.matricula.dto.Dtos.RequisitoResponse;
import pe.edu.upeu.eduspace.matricula.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.matricula.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.matricula.repository.MatriculaRepository;
import pe.edu.upeu.eduspace.matricula.repository.RequisitoRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RequisitoService {

    private final RequisitoRepository requisitoRepository;
    private final MatriculaRepository matriculaRepository;

    @Transactional
    public RequisitoResponse marcar(Long matriculaId, String tipo, RequisitoRequest req, Long usuarioId) {
        String t = tipo.toUpperCase();
        if (!MatriculaService.REQUISITOS.contains(t)) {
            throw new BusinessRuleException("Tipo de requisito inválido: " + tipo + ". Válidos: " + MatriculaService.REQUISITOS);
        }
        if (!matriculaRepository.existsById(matriculaId)) {
            throw new ResourceNotFoundException("Matrícula no encontrada: " + matriculaId);
        }
        var r = requisitoRepository.findByMatriculaIdAndTipo(matriculaId, t)
            .orElseThrow(() -> new ResourceNotFoundException("Requisito no encontrado: " + t));
        r.setCumplido(req.cumplido());
        r.setObservacion(req.observacion());
        r.setVerificadoPor(usuarioId);
        r.setVerificadoEn(LocalDateTime.now());
        return MatriculaMapper.toResponse(requisitoRepository.save(r));
    }
}
