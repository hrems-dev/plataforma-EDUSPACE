package pe.edu.upeu.eduspace.pago.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.upeu.eduspace.pago.entity.Cuota;
import pe.edu.upeu.eduspace.pago.repository.CuotaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/pagos")
@RequiredArgsConstructor
@Tag(name = "Internal Pago", description = "Endpoints de comunicación interna directa entre microservicios")
public class InternalPagoController {

    private final CuotaRepository cuotaRepository;

    @GetMapping("/alumno/{id}/deuda")
    @Operation(summary = "¿El alumno tiene deuda vencida? (Usado por matricula-ms para ratificación anual)")
    public ResponseEntity<Map<String, Object>> consultarDeudaVencida(@PathVariable Long id) {
        List<Cuota> vencidas = cuotaRepository.findDeudasVencidasByAlumnoId(id);
        boolean tieneDeuda = !vencidas.isEmpty();
        BigDecimal montoTotal = vencidas.stream().map(Cuota::getSaldo).reduce(BigDecimal.ZERO, BigDecimal::add);

        return ResponseEntity.ok(Map.of(
            "alumnoId", id,
            "tieneDeudaVencida", tieneDeuda,
            "montoVencido", montoTotal,
            "cuotasVencidasCount", vencidas.size()
        ));
    }
}

