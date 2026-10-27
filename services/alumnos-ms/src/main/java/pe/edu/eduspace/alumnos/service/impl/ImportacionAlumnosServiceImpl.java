package pe.edu.eduspace.alumnos.service.impl;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.edu.eduspace.alumnos.dto.AlumnoCreateRequest;
import pe.edu.eduspace.alumnos.dto.AlumnoResponse;
import pe.edu.eduspace.alumnos.dto.ApoderadoRequest;
import pe.edu.eduspace.alumnos.dto.ImportacionResponse;
import pe.edu.eduspace.alumnos.dto.VinculoApoderadoRequest;
import pe.edu.eduspace.alumnos.entity.Parentesco;
import pe.edu.eduspace.alumnos.exception.DatosInvalidosException;
import pe.edu.eduspace.alumnos.service.AlumnoService;
import pe.edu.eduspace.alumnos.service.ImportacionAlumnosService;
import pe.edu.eduspace.alumnos.util.CsvAlumnos;

/**
 * No es transaccional a propósito: cada fila llama a {@link AlumnoService#crear}, que abre su propia transacción,
 * así una fila inválida o duplicada no revierte las demás.
 */
@Service
@RequiredArgsConstructor
public class ImportacionAlumnosServiceImpl implements ImportacionAlumnosService {

    static final int MAX_FILAS = 500;
    private static final List<String> OBLIGATORIAS = List.of("numeroDocumento", "nombres", "apellidoPaterno",
            "fechaNacimiento", "sexo", "apoderadoNumeroDocumento", "apoderadoNombres", "apoderadoApellidos", "apoderadoParentesco");

    private final AlumnoService alumnoService;
    private final Validator validator;

    @Override
    public ImportacionResponse importar(InputStream csv) {
        List<String> lineas = leer(csv);
        if (lineas.isEmpty()) {
            throw new DatosInvalidosException("El archivo está vacío");
        }
        char delim = CsvAlumnos.detectarDelimitador(lineas.get(0));
        List<String> encabezados = CsvAlumnos.dividir(lineas.get(0), delim);
        List<String> faltan = OBLIGATORIAS.stream().filter(c -> !encabezados.contains(c)).toList();
        if (!faltan.isEmpty()) {
            throw new DatosInvalidosException("Faltan columnas obligatorias: " + String.join(", ", faltan));
        }
        if (lineas.size() - 1 > MAX_FILAS) {
            throw new DatosInvalidosException("Máximo " + MAX_FILAS + " alumnos por archivo");
        }

        List<ImportacionResponse.Fila> filas = new ArrayList<>();
        for (int i = 1; i < lineas.size(); i++) {
            List<String> valores = CsvAlumnos.dividir(lineas.get(i), delim);
            Map<String, String> fila = new java.util.HashMap<>();
            for (int c = 0; c < encabezados.size(); c++) {
                fila.put(encabezados.get(c), c < valores.size() && !valores.get(c).isEmpty() ? valores.get(c) : null);
            }
            filas.add(procesar(i + 1, fila));
        }
        int creados = (int) filas.stream().filter(ImportacionResponse.Fila::creado).count();
        return new ImportacionResponse(filas.size(), creados, filas.size() - creados, filas);
    }

    private ImportacionResponse.Fila procesar(int linea, Map<String, String> f) {
        String doc = f.get("numeroDocumento");
        try {
            AlumnoCreateRequest request = armar(f);
            String errores = validator.validate(request).stream()
                    .map((ConstraintViolation<AlumnoCreateRequest> v) -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted().collect(Collectors.joining("; "));
            if (!errores.isEmpty()) {
                return new ImportacionResponse.Fila(linea, doc, false, null, null, errores);
            }
            AlumnoResponse creado = alumnoService.crear(request);
            return new ImportacionResponse.Fila(linea, doc, true, creado.id(), creado.codigo(), null);
        } catch (RuntimeException ex) {
            return new ImportacionResponse.Fila(linea, doc, false, null, null, ex.getMessage());
        }
    }

    private AlumnoCreateRequest armar(Map<String, String> f) {
        LocalDate nacimiento;
        try {
            nacimiento = f.get("fechaNacimiento") == null ? null : LocalDate.parse(f.get("fechaNacimiento"));
        } catch (DateTimeParseException ex) {
            throw new DatosInvalidosException("fechaNacimiento debe ser AAAA-MM-DD");
        }
        Parentesco parentesco;
        try {
            parentesco = Parentesco.valueOf(String.valueOf(f.get("apoderadoParentesco")).toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new DatosInvalidosException("apoderadoParentesco inválido (use " + java.util.Arrays.toString(Parentesco.values()) + ")");
        }
        ApoderadoRequest apoderado = new ApoderadoRequest(f.get("apoderadoTipoDocumento"), f.get("apoderadoNumeroDocumento"),
                f.get("apoderadoNombres"), f.get("apoderadoApellidos"), f.get("apoderadoTelefono"), null,
                f.get("apoderadoEmail"), null, null);
        // único apoderado: es el principal y el responsable de pago
        VinculoApoderadoRequest vinculo = new VinculoApoderadoRequest(null, apoderado, parentesco, true, true, null, null);
        return new AlumnoCreateRequest(f.get("tipoDocumento"), f.get("numeroDocumento"), f.get("nombres"),
                f.get("apellidoPaterno"), f.get("apellidoMaterno"), nacimiento,
                f.get("sexo") == null ? null : f.get("sexo").toUpperCase(), null, f.get("direccion"), f.get("distrito"),
                f.get("telefono"), f.get("email"), null, List.of(vinculo));
    }

    private List<String> leer(InputStream in) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<String> lineas = new ArrayList<>();
            String l;
            while ((l = r.readLine()) != null) {
                if (lineas.isEmpty() && l.startsWith("﻿")) {
                    l = l.substring(1);
                }
                if (!l.isBlank()) {
                    lineas.add(l);
                }
            }
            return lineas;
        } catch (IOException ex) {
            throw new DatosInvalidosException("No se pudo leer el archivo");
        }
    }
}
