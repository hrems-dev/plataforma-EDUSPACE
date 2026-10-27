package pe.edu.upeu.eduspace.pago.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pe.edu.upeu.eduspace.pago.entity.AlertaAdmin;
import pe.edu.upeu.eduspace.pago.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.pago.repository.AlertaAdminRepository;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pagos/alertas")
@RequiredArgsConstructor
@Tag(name = "Alertas administrativas", description = "Tarifas faltantes y cuotas por decidir")
public class AlertaController {

    private final AlertaAdminRepository repo;

    @GetMapping
    @Operation(summary = "Alertas para administración (por defecto las pendientes)")
    public ResponseEntity<List<AlertaAdmin>> listar(@RequestParam(defaultValue = "false") boolean resueltas) {
        return ResponseEntity.ok(repo.findByResueltaOrderByCreadaEnDesc(resueltas));
    }

    @PatchMapping("/{id}/resolver")
    @Transactional
    @Operation(summary = "Marcar una alerta como resuelta")
    public ResponseEntity<AlertaAdmin> resolver(@PathVariable Long id) {
        AlertaAdmin a = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Alerta no encontrada: " + id));
        a.setResuelta(true);
        a.setResueltaEn(LocalDateTime.now());
        return ResponseEntity.ok(repo.save(a));
    }
}
