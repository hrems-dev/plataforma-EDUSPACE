package pe.edu.eduspace.planificacion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pe.edu.eduspace.planificacion.entity.Curso;
import pe.edu.eduspace.planificacion.entity.Docente;
import pe.edu.eduspace.planificacion.entity.Nivel;

class AsignacionPorEspecialidadTest {

    private Docente docente(String especialidad) {
        Docente d = new Docente();
        d.setEspecialidad(especialidad);
        return d;
    }

    private Curso curso(String nombre, String area) {
        Curso c = new Curso();
        c.setNombre(nombre);
        c.setArea(area);
        return c;
    }

    private Nivel nivel(String codigo) {
        Nivel n = new Nivel();
        n.setCodigo(codigo);
        return n;
    }

    @Test
    void laEspecialidadIgualAlCursoTienePrioridadMaxima() {
        assertThat(HorarioGeneradorServiceImpl.prioridad(docente("Educación Física"), curso("Educación Física", null), nivel("PRIMARIA"))).isZero();
        assertThat(HorarioGeneradorServiceImpl.prioridad(docente("MATEMÁTICA"), curso("Matemática", "Ciencias"), nivel("SECUNDARIA"))).isZero();
    }

    @Test
    void unDocenteDePrimariaPuedeDictarCualquierCursoDePrimaria() {
        assertThat(HorarioGeneradorServiceImpl.prioridad(docente("Educación Primaria"), curso("Comunicación", null), nivel("PRIMARIA"))).isEqualTo(1);
    }

    @Test
    void unGeneralistaNoDictaCursosDeSecundaria() {
        assertThat(HorarioGeneradorServiceImpl.prioridad(docente("Educación Primaria"), curso("Física", null), nivel("SECUNDARIA"))).isEqualTo(2);
    }

    @Test
    void sinEspecialidadOConDatoQueNoCorrespondeNoSeAsigna() {
        assertThat(HorarioGeneradorServiceImpl.prioridad(docente(null), curso("Inglés", null), nivel("PRIMARIA"))).isEqualTo(2);
        assertThat(HorarioGeneradorServiceImpl.prioridad(docente("Dato de prueba 19"), curso("Inglés", null), nivel("PRIMARIA"))).isEqualTo(2);
    }
}
