package pe.edu.upeu.eduspace.matricula.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Alta de cuentas de acceso (auth-ms, solo con el token de servicio). */
@FeignClient(name = "auth-ms")
public interface AuthClient {

    @PostMapping("/internal/cuentas")
    Cuenta crear(@RequestBody CuentaPersona body);

    record CuentaPersona(String nombres, String apellidos, String documento, String rol, String email) {}

    record Cuenta(Long id, String username) {}
}
