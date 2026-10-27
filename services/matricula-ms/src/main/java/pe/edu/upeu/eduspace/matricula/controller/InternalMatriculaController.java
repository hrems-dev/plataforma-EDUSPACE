package pe.edu.upeu.eduspace.matricula.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.edu.upeu.eduspace.matricula.dto.Dtos.InternalMatricula;
import pe.edu.upeu.eduspace.matricula.service.MatriculaService;

import java.util.List;

/** Solo accesible dentro de la red: el Gateway no enruta /internal/**. */
@RestController
@RequestMapping("/internal/matriculas")
@RequiredArgsConstructor
@Tag(name = "Internal Matrícula")
public class InternalMatriculaController {

    private final MatriculaService service;

    @GetMapping("/seccion/{seccionId}")
    public List<InternalMatricula> deSeccion(@PathVariable Long seccionId) {
        return service.vivasDeSeccion(seccionId);
    }

    @GetMapping("/alumno/{alumnoId}")
    public InternalMatricula deAlumno(@PathVariable Long alumnoId, @RequestParam(required = false) Long anioId) {
        return service.vigenteDeAlumno(alumnoId, anioId);
    }
}
