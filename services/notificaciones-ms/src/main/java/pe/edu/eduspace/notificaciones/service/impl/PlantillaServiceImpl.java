package pe.edu.eduspace.notificaciones.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import pe.edu.eduspace.notificaciones.dto.PlantillaCrearRequest;
import pe.edu.eduspace.notificaciones.dto.PlantillaRequest;
import pe.edu.eduspace.notificaciones.dto.PlantillaResponse;
import pe.edu.eduspace.notificaciones.entity.Canal;
import pe.edu.eduspace.notificaciones.entity.PlantillaNotificacion;
import pe.edu.eduspace.notificaciones.exception.BusinessRuleException;
import pe.edu.eduspace.notificaciones.exception.ResourceNotFoundException;
import pe.edu.eduspace.notificaciones.mapper.NotificacionMapper;
import pe.edu.eduspace.notificaciones.repository.PlantillaRepository;
import pe.edu.eduspace.notificaciones.service.PlantillaService;

@Service
@RequiredArgsConstructor
@Transactional
public class PlantillaServiceImpl implements PlantillaService {

    static final String SIN_DATO = "(sin dato)";
    /** Códigos que usan los avisos automáticos (eventos de los demás servicios): borrarlos los dejaría sin texto. */
    static final Set<String> DEL_SISTEMA = Set.of("ASISTENCIA_FALTA", "BOLETIN_GENERADO", "EXAMEN_CALIFICADO", "EXAMEN_PUBLICADO",
            "HORARIO_PUBLICADO", "JUSTIFICACION_RESUELTA", "MATRICULA_ANULADA", "MATRICULA_CONFIRMADA", "MATRICULA_CREADA",
            "PAGO_CONFIRMADO", "PAGO_POR_VENCER", "PAGO_VENCIDO", "TAREA_CALIFICADA", "TAREA_PUBLICADA", "TAREA_RECORDATORIO");
    private static final Pattern MARCADOR = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");

    private final PlantillaRepository repository;
    private final NotificacionMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<PlantillaNotificacion> activa(String codigo, Canal canal) {
        return repository.findByCodigoAndCanal(codigo, canal).filter(p -> Boolean.TRUE.equals(p.getActiva()));
    }

    @Override
    public Texto armar(PlantillaNotificacion plantilla, Map<String, String> datos) {
        String asunto = plantilla.getAsunto() == null ? "" : reemplazar(plantilla.getAsunto(), datos);
        return new Texto(asunto, reemplazar(plantilla.getCuerpo(), datos));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlantillaResponse> listar() {
        return repository.findAllByOrderByCodigoAscCanalAsc().stream().map(mapper::toResponse).toList();
    }

    @Override
    public PlantillaResponse crear(PlantillaCrearRequest request) {
        if (repository.findByCodigoAndCanal(request.codigo(), request.canal()).isPresent()) {
            throw new BusinessRuleException("Ya existe una plantilla " + request.codigo() + " para el canal " + request.canal());
        }
        PlantillaNotificacion p = new PlantillaNotificacion();
        p.setCodigo(request.codigo());
        p.setCanal(request.canal());
        p.setAsunto(request.asunto());
        p.setCuerpo(request.cuerpo());
        p.setActiva(request.activa() == null || request.activa());
        return mapper.toResponse(repository.save(p));
    }

    @Override
    public void eliminar(Long id) {
        PlantillaNotificacion p = repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Plantilla", id));
        if (DEL_SISTEMA.contains(p.getCodigo())) {
            throw new BusinessRuleException("«" + p.getCodigo() + "» es una plantilla del sistema: no se elimina, solo se desactiva");
        }
        repository.delete(p);
    }

    @Override
    public PlantillaResponse actualizar(Long id, PlantillaRequest request) {
        PlantillaNotificacion plantilla = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Plantilla", id));
        plantilla.setAsunto(request.asunto());
        plantilla.setCuerpo(request.cuerpo());
        if (request.activa() != null) {
            plantilla.setActiva(request.activa());
        }
        return mapper.toResponse(repository.save(plantilla));
    }

    private String reemplazar(String texto, Map<String, String> datos) {
        Matcher m = MARCADOR.matcher(texto);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String valor = datos.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(valor == null || valor.isBlank() ? SIN_DATO : valor));
        }
        m.appendTail(out);
        return out.toString();
    }
}
