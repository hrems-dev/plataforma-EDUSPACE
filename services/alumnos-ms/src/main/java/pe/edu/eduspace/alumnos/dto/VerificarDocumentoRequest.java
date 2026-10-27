package pe.edu.eduspace.alumnos.dto;

public record VerificarDocumentoRequest(Boolean verificado) {
    public boolean valor() {
        return verificado == null || verificado;
    }
}
