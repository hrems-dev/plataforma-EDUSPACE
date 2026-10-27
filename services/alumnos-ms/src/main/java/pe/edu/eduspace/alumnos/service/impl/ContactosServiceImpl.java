package pe.edu.eduspace.alumnos.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.alumnos.dto.ContactosNotificacionResponse;
import pe.edu.eduspace.alumnos.entity.Alumno;
import pe.edu.eduspace.alumnos.exception.ResourceNotFoundException;
import pe.edu.eduspace.alumnos.mapper.AlumnoMapper;
import pe.edu.eduspace.alumnos.repository.AlumnoApoderadoRepository;
import pe.edu.eduspace.alumnos.repository.AlumnoRepository;
import pe.edu.eduspace.alumnos.service.ContactosService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContactosServiceImpl implements ContactosService {

    private final AlumnoRepository alumnoRepository;
    private final AlumnoApoderadoRepository vinculoRepository;
    private final AlumnoMapper mapper;

    @Override
    public ContactosNotificacionResponse contactos(Long alumnoId) {
        Alumno alumno = alumnoRepository.findById(alumnoId).orElseThrow(() -> ResourceNotFoundException.of("Alumno", alumnoId));
        return new ContactosNotificacionResponse(alumno.getId(), alumno.nombreCompleto(), alumno.getUsuarioId(),
                vinculoRepository.findByAlumno(alumnoId).stream().map(mapper::toContacto).toList());
    }
}
