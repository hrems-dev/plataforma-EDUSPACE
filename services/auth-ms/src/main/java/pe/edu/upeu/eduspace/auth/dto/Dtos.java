package pe.edu.upeu.eduspace.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class Dtos {

    private Dtos() {}

    public record LoginRequest(@NotBlank(message = "El usuario es obligatorio") String username,
                               @NotBlank(message = "La clave es obligatoria") String password) {}

    public record RefreshRequest(@JsonProperty("refresh_token") @NotBlank(message = "refresh_token es obligatorio") String refreshToken) {}

    public record TokenResponse(@JsonProperty("access_token") String accessToken,
                                @JsonProperty("refresh_token") String refreshToken,
                                @JsonProperty("token_type") String tokenType,
                                @JsonProperty("expires_in") long expiresIn,
                                @JsonProperty("debe_cambiar_clave") boolean debeCambiarClave) {}

    public record CambiarClaveRequest(@NotBlank String claveActual, @NotBlank String claveNueva) {}

    public record UsuarioCreateRequest(
        @NotBlank @Size(min = 3, max = 80) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "username solo admite letras, números, punto, guion y guion bajo") String username,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(max = 100) String nombres,
        @NotBlank @Size(max = 100) String apellidos,
        @NotEmpty(message = "Indique al menos un rol") Set<String> roles,
        @Size(max = 72) String clave) {}

    public record UsuarioUpdateRequest(
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(max = 100) String nombres,
        @NotBlank @Size(max = 100) String apellidos) {}

    public record RolesRequest(@NotEmpty(message = "Indique al menos un rol") Set<String> roles) {}

    public record UsuarioResponse(Long id, String username, String email, String nombres, String apellidos, boolean activo,
                                  boolean bloqueado, boolean debeCambiarClave, List<String> roles, LocalDateTime ultimoLogin,
                                  LocalDateTime creadoEn) {}

    /** La clave temporal se entrega una sola vez, en la respuesta de creación / restablecimiento. */
    public record UsuarioConClaveTemporal(UsuarioResponse usuario, String claveTemporal) {}

    public record PageResponse<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {}
}
