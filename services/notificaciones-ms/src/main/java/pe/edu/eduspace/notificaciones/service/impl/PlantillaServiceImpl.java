package pe.edu.eduspace.notificaciones.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.eduspace.notificaciones.dto.PlantillaRequest;
import pe.edu.eduspace.notificaciones.dto.PlantillaResponse;
import pe.edu.eduspace.notificaciones.entity.Canal;
import pe.edu.eduspace.notificaciones.entity.PlantillaNotificacion;
import pe.edu.eduspace.notificaciones.exception.ResourceNotFoundException;
import pe.edu.eduspace.notificaciones.mapper.NotificacionMapper;
import pe.edu.eduspace.notificaciones.repository.PlantillaRepository;
import pe.edu.eduspace.notificaciones.service.PlantillaService;

@Service
@RequiredArgsConstructor
@Transactional
public class PlantillaServiceImpl implements PlantillaService {

    static final String SIN_DATO = "(sin dato)";
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
