package pe.edu.eduspace.alumnos.controller;

import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.eduspace.alumnos.dto.DescargaResponse;
import pe.edu.eduspace.alumnos.dto.DocumentoResponse;
import pe.edu.eduspace.alumnos.dto.VerificarDocumentoRequest;
import pe.edu.eduspace.alumnos.entity.TipoDocumento;
import pe.edu.eduspace.alumnos.exception.StorageException;
import pe.edu.eduspace.alumnos.service.DocumentoService;
import pe.edu.eduspace.alumnos.util.Respuestas;

@RestController
@RequestMapping("/api/v1/alumnos/{alumnoId}/documentos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SECRETARIA')")
public class DocumentoController {

    private final DocumentoService service;

    @GetMapping
    public List<DocumentoResponse> listar(@PathVariable Long alumnoId) {
        return service.listar(alumnoId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentoResponse> subir(@PathVariable Long alumnoId, @RequestParam TipoDocumento tipo,
                                                   @RequestParam("archivo") MultipartFile archivo) {
        try {
            DocumentoResponse creado = service.subir(alumnoId, tipo, archivo.getOriginalFilename(),
                    archivo.getContentType(), archivo.getSize(), archivo.getInputStream());
            return Respuestas.creado(creado.id(), creado);
        } catch (IOException ex) {
            throw new StorageException("No se pudo leer el archivo recibido", ex);
        }
    }

    /** Devuelve una URL firmada de descarga con vencimiento corto (el archivo no pasa por el servicio). */
    @GetMapping("/{documentoId}/descarga")
    public DescargaResponse descargar(@PathVariable Long alumnoId, @PathVariable Long documentoId) {
        return service.descargar(alumnoId, documentoId);
    }

    @PatchMapping("/{documentoId}/verificado")
    public DocumentoResponse verificar(@PathVariable Long alumnoId, @PathVariable Long documentoId,
                                       @RequestBody(required = false) VerificarDocumentoRequest request) {
        boolean valor = request == null || request.valor();
        return service.verificar(alumnoId, documentoId, valor);
    }

    @DeleteMapping("/{documentoId}")
    public ResponseEntity<Void> eliminar(@PathVariable Long alumnoId, @PathVariable Long documentoId) {
        service.eliminar(alumnoId, documentoId);
        return ResponseEntity.noContent().build();
    }
}
