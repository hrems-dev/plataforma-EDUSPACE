package pe.edu.upeu.eduspace.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.eduspace.auth.dto.Dtos.*;
import pe.edu.upeu.eduspace.auth.entity.Rol;
import pe.edu.upeu.eduspace.auth.entity.Usuario;
import pe.edu.upeu.eduspace.auth.exception.BusinessRuleException;
import pe.edu.upeu.eduspace.auth.exception.ResourceNotFoundException;
import pe.edu.upeu.eduspace.auth.repository.RolRepository;
import pe.edu.upeu.eduspace.auth.repository.UsuarioRepository;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Administración de usuarios (solo ADMIN). No hay borrado físico: otros servicios guardan {@code usuario_id}, así que
 * "eliminar" desactiva. Nunca se deja al sistema sin un ADMIN activo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder encoder;
    private final RefreshTokenService refresh;
    private final AuthService authService;

    @Transactional(readOnly = true)
    public PageResponse<UsuarioResponse> listar(String texto, String rol, Boolean activo, Pageable pageable) {
        var page = usuarios.buscar(texto == null ? "" : texto.trim(), rol == null ? "" : rol.trim().toUpperCase(), activo, pageable);
        return new PageResponse<>(page.getContent().stream().map(UsuarioService::toResponse).toList(),
            page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtener(Long id) {
        return toResponse(buscar(id));
    }

    /** Sin {@code clave}: se genera una temporal y el usuario debe cambiarla en su primer ingreso. */
    @Transactional
    public UsuarioConClaveTemporal crear(UsuarioCreateRequest req) {
        if (usuarios.existsByUsernameIgnoreCase(req.username())) throw new BusinessRuleException("El usuario '" + req.username() + "' ya existe.");
        if (usuarios.existsByEmailIgnoreCase(req.email())) throw new BusinessRuleException("El correo '" + req.email() + "' ya está registrado.");
        boolean temporal = req.clave() == null || req.clave().isBlank();
        String clave = temporal ? generarClave() : req.clave();
        if (!temporal) authService.validarClave(clave);
        Usuario u = usuarios.save(Usuario.builder().username(req.username().trim()).email(req.email().trim())
            .nombres(req.nombres().trim()).apellidos(req.apellidos().trim()).passwordHash(encoder.encode(clave))
            .debeCambiarClave(temporal).roles(resolverRoles(req.roles())).build());
        return new UsuarioConClaveTemporal(toResponse(u), temporal ? clave : null);
    }

    /**
     * Cuenta para una persona del colegio (alumno, docente, apoderado, caja…): usuario {@code nombre.apellido} (con número si ya existe)
     * y clave inicial igual a su documento; queda obligada a cambiarla en el primer ingreso. Nunca crea cuentas de ADMIN.
     */
    @Transactional
    public UsuarioConClaveTemporal crearCuentaPersona(CuentaPersonaRequest req) {
        String rol = req.rol().trim().toUpperCase().replace("ROLE_", "");
        if ("ADMIN".equals(rol)) throw new BusinessRuleException("Las cuentas de administración no se crean desde aquí.");
        String username = usernameLibre(req.nombres(), req.apellidos());
        String email = req.email() == null || req.email().isBlank() ? username + "@cuentas.eduspace.pe" : req.email().trim();
        if (usuarios.existsByEmailIgnoreCase(email)) email = username + "@cuentas.eduspace.pe";
        Usuario u = usuarios.save(Usuario.builder().username(username).email(email).nombres(req.nombres().trim())
            .apellidos(req.apellidos().trim()).passwordHash(encoder.encode(req.documento().trim()))
            .debeCambiarClave(true).roles(resolverRoles(Set.of(rol))).build());
        log.info("Cuenta creada: usuario={} rol={}", username, rol);
        return new UsuarioConClaveTemporal(toResponse(u), null);
    }

    /** Restaura la cuenta: la clave vuelve a ser el documento y debe cambiarse al entrar. No aplica a ADMIN. */
    @Transactional
    public UsuarioResponse restablecerPorDocumento(Long id, RestablecerPorDocumentoRequest req) {
        Usuario u = buscar(id);
        if (u.esAdmin()) throw new BusinessRuleException("Las cuentas de administración no se restauran desde aquí.");
        u.setPasswordHash(encoder.encode(req.documento().trim()));
        u.setDebeCambiarClave(true);
        u.setBloqueadoHasta(null);
        u.setIntentosFallidos((short) 0);
        u.setActivo(true);
        u.setActualizadoEn(LocalDateTime.now());
        usuarios.save(u);
        refresh.revocarTodos(id);
        return toResponse(u);
    }

    private String usernameLibre(String nombres, String apellidos) {
        String base = baseUsername(nombres, apellidos);
        String candidato = base;
        for (int n = 2; usuarios.existsByUsernameIgnoreCase(candidato); n++) candidato = base + n;
        return candidato;
    }

    /** Primer nombre + punto + primer apellido, en minúsculas y sin tildes ni símbolos (ej. «María José» «Aguilar Ortiz» → maria.aguilar). */
    static String baseUsername(String nombres, String apellidos) {
        String n = soloLetras(primera(nombres)), a = soloLetras(primera(apellidos));
        String base = (n + "." + a).replaceAll("^\\.|\\.$", "");
        return base.length() >= 3 ? base : (base + "usr");
    }

    private static String primera(String texto) {
        String[] partes = texto.trim().split("\\s+");
        return partes.length == 0 ? "" : partes[0];
    }

    private static String soloLetras(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    @Transactional
    public UsuarioResponse actualizar(Long id, UsuarioUpdateRequest req) {
        Usuario u = buscar(id);
        if (!u.getEmail().equalsIgnoreCase(req.email()) && usuarios.existsByEmailIgnoreCase(req.email())) {
            throw new BusinessRuleException("El correo '" + req.email() + "' ya está registrado.");
        }
        u.setEmail(req.email().trim());
        u.setNombres(req.nombres().trim());
        u.setApellidos(req.apellidos().trim());
        u.setActualizadoEn(LocalDateTime.now());
        return toResponse(usuarios.save(u));
    }

    @Transactional
    public UsuarioResponse asignarRoles(Long id, RolesRequest req) {
        Usuario u = buscar(id);
        Set<Rol> nuevos = resolverRoles(req.roles());
        boolean pierdeAdmin = u.esAdmin() && nuevos.stream().noneMatch(r -> "ADMIN".equals(r.getNombre()));
        if (pierdeAdmin) exigirOtroAdmin(u);
        u.setRoles(nuevos);
        u.setActualizadoEn(LocalDateTime.now());
        refresh.revocarTodos(id);                // el token vigente seguiría con los roles viejos hasta vencer
        return toResponse(usuarios.save(u));
    }

    @Transactional
    public UsuarioResponse cambiarEstado(Long id, boolean activo) {
        Usuario u = buscar(id);
        if (!activo && u.esAdmin() && Boolean.TRUE.equals(u.getActivo())) exigirOtroAdmin(u);
        u.setActivo(activo);
        u.setActualizadoEn(LocalDateTime.now());
        if (!activo) refresh.revocarTodos(id);
        return toResponse(usuarios.save(u));
    }

    @Transactional
    public UsuarioResponse desbloquear(Long id) {
        Usuario u = buscar(id);
        u.setBloqueadoHasta(null);
        u.setIntentosFallidos((short) 0);
        u.setActualizadoEn(LocalDateTime.now());
        return toResponse(usuarios.save(u));
    }

    @Transactional
    public UsuarioConClaveTemporal restablecerClave(Long id) {
        Usuario u = buscar(id);
        String clave = generarClave();
        u.setPasswordHash(encoder.encode(clave));
        u.setDebeCambiarClave(true);
        u.setBloqueadoHasta(null);
        u.setIntentosFallidos((short) 0);
        u.setActualizadoEn(LocalDateTime.now());
        usuarios.save(u);
        refresh.revocarTodos(id);
        return new UsuarioConClaveTemporal(toResponse(u), clave);
    }

    private void exigirOtroAdmin(Usuario u) {
        if (usuarios.contarAdminsActivos() <= 1 && Boolean.TRUE.equals(u.getActivo())) {
            throw new BusinessRuleException("No se puede quitar al último ADMIN activo del sistema.");
        }
    }

    private Set<Rol> resolverRoles(Set<String> nombres) {
        Set<String> pedidos = new HashSet<>();
        nombres.forEach(n -> pedidos.add(n.trim().toUpperCase().replace("ROLE_", "")));
        List<Rol> encontrados = roles.findByNombreIn(pedidos);
        if (encontrados.size() != pedidos.size()) {
            Set<String> faltan = new HashSet<>(pedidos);
            encontrados.forEach(r -> faltan.remove(r.getNombre()));
            throw new BusinessRuleException("Roles inexistentes: " + faltan);
        }
        return new HashSet<>(encontrados);
    }

    private Usuario buscar(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + id));
    }

    static String generarClave() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
        return sb + "a1";           // garantiza letra y número
    }

    static UsuarioResponse toResponse(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getUsername(), u.getEmail(), u.getNombres(), u.getApellidos(),
            Boolean.TRUE.equals(u.getActivo()), u.estaBloqueado(), Boolean.TRUE.equals(u.getDebeCambiarClave()),
            u.getRoles().stream().map(Rol::getNombre).sorted().toList(), u.getUltimoLogin(), u.getCreadoEn());
    }
}
