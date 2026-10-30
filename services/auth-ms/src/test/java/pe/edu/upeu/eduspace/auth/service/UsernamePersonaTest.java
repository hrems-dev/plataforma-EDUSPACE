package pe.edu.upeu.eduspace.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UsernamePersonaTest {

    @Test
    void usaPrimerNombreYPrimerApellidoSinTildes() {
        assertThat(UsuarioService.baseUsername("María José", "Aguilar Ortiz")).isEqualTo("maria.aguilar");
        assertThat(UsuarioService.baseUsername("Ñahui", "Quispe")).isEqualTo("nahui.quispe");
    }

    @Test
    void quitaSimbolosYGarantizaLongitudMinima() {
        assertThat(UsuarioService.baseUsername("D'Angelo", "O’Brien")).isEqualTo("dangelo.obrien");
        assertThat(UsuarioService.baseUsername("Al", "")).hasSizeGreaterThanOrEqualTo(3);
    }
}
