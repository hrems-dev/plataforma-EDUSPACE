package pe.edu.eduspace.planificacion.horario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pe.edu.eduspace.planificacion.horario.PlanificadorHorario.Leccion;
import pe.edu.eduspace.planificacion.horario.PlanificadorHorario.Problema;

class PlanificadorHorarioTest {

    private static final int DIAS = 5;
    private static final int BLOQUES = 5;

    private Problema problema(int secciones, int docentes, int cursosPorSeccion, int horas, boolean[][] ocupDocente) {
        List<Leccion> lecciones = new ArrayList<>();
        for (int s = 0; s < secciones; s++) {
            for (int c = 0; c < cursosPorSeccion; c++) {
                int docente = (s * 3 + c) % docentes;
                for (int h = 0; h < horas; h++) lecciones.add(new Leccion(s, docente, c, s));
            }
        }
        int e = DIAS * BLOQUES;
        boolean[][] permitido = new boolean[secciones][e];
        for (boolean[] fila : permitido) java.util.Arrays.fill(fila, true);
        return new Problema(DIAS, BLOQUES, lecciones, permitido, new boolean[secciones][e],
                ocupDocente != null ? ocupDocente : new boolean[docentes][e], new boolean[secciones][e], cursosPorSeccion);
    }

    /** Verifica que nadie esté en dos lugares a la vez: sección, docente y aula únicos por espacio. */
    private void sinCruces(Problema p, int[] sol) {
        Set<String> usados = new HashSet<>();
        for (int l = 0; l < sol.length; l++) {
            if (sol[l] < 0) continue;
            Leccion x = p.lecciones.get(l);
            assertThat(usados.add("S" + x.seccion() + "-" + sol[l])).as("sección repetida").isTrue();
            assertThat(usados.add("D" + x.docente() + "-" + sol[l])).as("docente en dos secciones a la vez").isTrue();
            if (x.aula() >= 0) assertThat(usados.add("A" + x.aula() + "-" + sol[l])).as("aula repetida").isTrue();
            assertThat(p.ocupDocente[x.docente()][sol[l]]).as("espacio ya ocupado del docente").isFalse();
        }
    }

    private long colocadas(int[] sol) {
        return java.util.Arrays.stream(sol).filter(s -> s >= 0).count();
    }

    @Test
    void colocaTodoSinCrucesCuandoHayEspacio() {
        Problema p = problema(6, 12, 5, 4, null); // 6 secciones × 20 horas, 12 docentes
        int[] sol = PlanificadorHorario.resolver(p, 7);
        assertThat(colocadas(sol)).isEqualTo(p.lecciones.size());
        sinCruces(p, sol);
    }

    @Test
    void resuelveSeccionesCompletasQueCompartenDocentes() {
        // cada sección usa los 25 espacios de la semana (5 cursos × 5 h) y los docentes se reparten entre secciones
        Problema p = problema(8, 12, 5, 5, null);
        int[] sol = PlanificadorHorario.resolver(p, 11);
        assertThat(colocadas(sol)).isEqualTo(p.lecciones.size());
        sinCruces(p, sol);
    }

    @Test
    void esDeterministaParaLaMismaSemilla() {
        Problema p = problema(5, 8, 5, 4, null);
        assertThat(PlanificadorHorario.resolver(p, 3)).isEqualTo(PlanificadorHorario.resolver(p, 3));
    }

    @Test
    void respetaLosEspaciosYaOcupadosDeUnDocente() {
        int e = DIAS * BLOQUES;
        boolean[][] ocup = new boolean[4][e];
        for (int s = 0; s < 10; s++) ocup[0][s] = true; // el docente 0 ya tiene los dos primeros días
        Problema p = problema(3, 4, 4, 3, ocup);
        int[] sol = PlanificadorHorario.resolver(p, 5);
        sinCruces(p, sol);
        assertThat(colocadas(sol)).isEqualTo(p.lecciones.size());
    }

    @Test
    void siUnDocenteTieneMasHorasQueEspaciosColocaLoPosibleSinCruces() {
        // un solo docente con 30 horas en una semana de 25 espacios: 5 quedan sin colocar, ninguna cruzada
        List<Leccion> lecciones = new ArrayList<>();
        for (int s = 0; s < 3; s++) for (int h = 0; h < 10; h++) lecciones.add(new Leccion(s, 0, 0, s));
        int e = DIAS * BLOQUES;
        boolean[][] permitido = new boolean[3][e];
        for (boolean[] f : permitido) java.util.Arrays.fill(f, true);
        Problema p = new Problema(DIAS, BLOQUES, lecciones, permitido, new boolean[3][e], new boolean[1][e], new boolean[3][e], 1);
        int[] sol = PlanificadorHorario.resolver(p, 1);
        assertThat(colocadas(sol)).isEqualTo(25);
        sinCruces(p, sol);
    }

    @Test
    void unaSeccionSoloUsaLosBloquesDeSuTurno() {
        List<Leccion> lecciones = new ArrayList<>();
        for (int h = 0; h < 6; h++) lecciones.add(new Leccion(0, 0, 0, 0));
        int e = DIAS * BLOQUES;
        boolean[][] permitido = new boolean[1][e];
        for (int s = 0; s < e; s++) permitido[0][s] = (s % BLOQUES) < 3; // solo los 3 primeros bloques de cada día
        Problema p = new Problema(DIAS, BLOQUES, lecciones, permitido, new boolean[1][e], new boolean[1][e], new boolean[1][e], 1);
        int[] sol = PlanificadorHorario.resolver(p, 2);
        for (int s : sol) assertThat(s % BLOQUES).isLessThan(3);
    }
}
