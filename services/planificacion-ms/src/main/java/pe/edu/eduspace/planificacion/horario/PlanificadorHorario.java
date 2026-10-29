package pe.edu.eduspace.planificacion.horario;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Armado automático de horarios sin cruces. No conoce JPA: trabaja con índices (secciones, docentes, aulas, cursos) y
 * "espacios" (día × bloque de clase), así se puede probar aislado.
 *
 * <p>Restricciones duras: una sección tiene una sola clase por espacio, un docente no puede estar en dos secciones a
 * la vez y un aula no se usa dos veces a la vez; además una sección solo usa los bloques de su turno. Las clases que ya
 * existen y no se tocan entran como espacios ocupados.
 *
 * <p>Método: (1) construcción voraz que coloca primero la clase con menos opciones libres (MRV) y prefiere repartir
 * cada curso en días distintos; (2) si quedan clases sin lugar, búsqueda local de mínimos conflictos con lista tabú:
 * se coloca la clase pendiente donde estorba a menos clases, y las desplazadas vuelven a buscar su lugar. Es
 * determinista para una misma semilla: simular y aplicar dan el mismo horario.
 */
public final class PlanificadorHorario {

    /** Una hora de clase por programar. Índices compactos; {@code aula = -1} si la sección no tiene aula. */
    public record Leccion(int seccion, int docente, int curso, int aula) {
    }

    /** Datos del problema. Espacio = {@code dia * bloques + bloque}. Las matrices {@code ocupado*} son clases fijas. */
    public static final class Problema {
        public final int dias;
        public final int bloques;
        public final List<Leccion> lecciones;
        public final boolean[][] permitido;   // [seccion][espacio]
        public final boolean[][] ocupSeccion; // [seccion][espacio]
        public final boolean[][] ocupDocente; // [docente][espacio]
        public final boolean[][] ocupAula;    // [aula][espacio]
        public final int cursos;

        public Problema(int dias, int bloques, List<Leccion> lecciones, boolean[][] permitido, boolean[][] ocupSeccion,
                        boolean[][] ocupDocente, boolean[][] ocupAula, int cursos) {
            this.dias = dias;
            this.bloques = bloques;
            this.lecciones = lecciones;
            this.permitido = permitido;
            this.ocupSeccion = ocupSeccion;
            this.ocupDocente = ocupDocente;
            this.ocupAula = ocupAula;
            this.cursos = cursos;
        }

        int espacios() {
            return dias * bloques;
        }
    }

    private static final int INTENTOS = 25;
    private static final int LIBRE = -1;
    private static final int FIJO = -2;

    private final Problema p;
    private final int e;
    private final int n;
    private final Random rng;

    // estado de la solución en curso
    private int[] espacio;
    private int[][] dueSeccion;
    private int[][] dueDocente;
    private int[][] dueAula;
    private int[][][] cursoDia;     // [seccion][curso][dia]
    private int[][] cargaDia;       // [seccion][dia]
    private int[][] cargaDocDia;    // [docente][dia]
    private int sinColocar;

    private PlanificadorHorario(Problema problema, long semilla) {
        this.p = problema;
        this.e = problema.espacios();
        this.n = problema.lecciones.size();
        this.rng = new Random(semilla);
    }

    /** Devuelve, por lección, el espacio asignado o -1 si no se logró colocar. */
    public static int[] resolver(Problema problema, long semilla) {
        PlanificadorHorario s = new PlanificadorHorario(problema, semilla);
        return s.resolver();
    }

    private int[] resolver() {
        int[] mejor = new int[n];
        Arrays.fill(mejor, -1);
        int mejorSin = n;
        long limite = System.nanoTime() + 20_000_000_000L; // tope de seguridad; en la práctica terminan antes los intentos
        for (int intento = 0; intento < INTENTOS && System.nanoTime() < limite; intento++) {
            reiniciar();
            construir();
            if (sinColocar > 0) {
                reparar(4000 + 30 * n, limite);
            }
            if (sinColocar < mejorSin) {
                mejorSin = sinColocar;
                mejor = espacio.clone();
            }
            if (mejorSin == 0) {
                break;
            }
        }
        return mejor;
    }

    // ------------------------------------------------------------------ estado
    private void reiniciar() {
        espacio = new int[n];
        Arrays.fill(espacio, -1);
        int ns = p.permitido.length;
        dueSeccion = marcar(p.ocupSeccion, ns);
        dueDocente = marcar(p.ocupDocente, p.ocupDocente.length);
        dueAula = marcar(p.ocupAula, p.ocupAula.length);
        cursoDia = new int[ns][Math.max(p.cursos, 1)][p.dias];
        cargaDia = new int[ns][p.dias];
        cargaDocDia = new int[p.ocupDocente.length][p.dias];
        sinColocar = n;
    }

    private int[][] marcar(boolean[][] fijo, int filas) {
        int[][] m = new int[filas][e];
        for (int i = 0; i < filas; i++) {
            for (int s = 0; s < e; s++) {
                m[i][s] = fijo[i][s] ? FIJO : LIBRE;
            }
        }
        return m;
    }

    private void colocar(int l, int s) {
        Leccion x = p.lecciones.get(l);
        int dia = s / p.bloques;
        espacio[l] = s;
        dueSeccion[x.seccion()][s] = l;
        dueDocente[x.docente()][s] = l;
        if (x.aula() >= 0) dueAula[x.aula()][s] = l;
        cursoDia[x.seccion()][x.curso()][dia]++;
        cargaDia[x.seccion()][dia]++;
        cargaDocDia[x.docente()][dia]++;
        sinColocar--;
    }

    private void quitar(int l) {
        Leccion x = p.lecciones.get(l);
        int s = espacio[l];
        int dia = s / p.bloques;
        dueSeccion[x.seccion()][s] = LIBRE;
        dueDocente[x.docente()][s] = LIBRE;
        if (x.aula() >= 0) dueAula[x.aula()][s] = LIBRE;
        cursoDia[x.seccion()][x.curso()][dia]--;
        cargaDia[x.seccion()][dia]--;
        cargaDocDia[x.docente()][dia]--;
        espacio[l] = -1;
        sinColocar++;
    }

    private boolean libre(Leccion x, int s) {
        return p.permitido[x.seccion()][s] && dueSeccion[x.seccion()][s] == LIBRE && dueDocente[x.docente()][s] == LIBRE
                && (x.aula() < 0 || dueAula[x.aula()][s] == LIBRE);
    }

    /** Menor es mejor: reparte el curso en días distintos (y, si repite el día, en bloques seguidos) y equilibra la carga. */
    private double puntaje(Leccion x, int s) {
        int dia = s / p.bloques;
        int bloque = s % p.bloques;
        int mismoDia = cursoDia[x.seccion()][x.curso()][dia];
        double pts = 0;
        if (mismoDia > 0) {
            boolean contiguo = false;
            for (int b : new int[] {bloque - 1, bloque + 1}) {
                if (b >= 0 && b < p.bloques) {
                    int otra = dueSeccion[x.seccion()][dia * p.bloques + b];
                    if (otra >= 0 && p.lecciones.get(otra).curso() == x.curso()) contiguo = true;
                }
            }
            pts += contiguo ? 5 : 14;
        }
        pts += cargaDia[x.seccion()][dia] + 0.25 * cargaDocDia[x.docente()][dia];
        return pts;
    }

    // ------------------------------------------------------------------ fase 1: voraz con MRV
    private void construir() {
        boolean[] descartada = new boolean[n];
        while (true) {
            int elegida = -1;
            int menor = Integer.MAX_VALUE;
            for (int l = 0; l < n; l++) {
                if (espacio[l] >= 0 || descartada[l]) continue;
                Leccion x = p.lecciones.get(l);
                int opciones = 0;
                for (int s = 0; s < e; s++) if (libre(x, s)) opciones++;
                if (opciones == 0) {
                    descartada[l] = true;
                    continue;
                }
                if (opciones < menor || (opciones == menor && rng.nextBoolean())) {
                    menor = opciones;
                    elegida = l;
                }
            }
            if (elegida < 0) return;
            Leccion x = p.lecciones.get(elegida);
            int mejor = -1;
            double mejorPts = Double.MAX_VALUE;
            for (int s = 0; s < e; s++) {
                if (!libre(x, s)) continue;
                double pts = puntaje(x, s) + rng.nextDouble() * 1.5;
                if (pts < mejorPts) {
                    mejorPts = pts;
                    mejor = s;
                }
            }
            colocar(elegida, mejor);
        }
    }

    // ------------------------------------------------------------------ fase 2: mínimos conflictos con tabú
    private void reparar(int maxIter, long limite) {
        int[][] tabu = new int[n][e];
        List<Integer> pendientes = new ArrayList<>();
        for (int l = 0; l < n; l++) if (espacio[l] < 0) pendientes.add(l);
        int[] mejorEstado = espacio.clone();
        int mejorSin = sinColocar;
        for (int iter = 1; iter <= maxIter && !pendientes.isEmpty(); iter++) {
            if ((iter & 255) == 0 && System.nanoTime() > limite) break;
            int l = pendientes.remove(rng.nextInt(pendientes.size()));
            Leccion x = p.lecciones.get(l);
            int mejorS = -1;
            int mejorCosto = Integer.MAX_VALUE;
            double mejorPts = Double.MAX_VALUE;
            for (int s = 0; s < e; s++) {
                if (!p.permitido[x.seccion()][s]) continue;
                int a = dueSeccion[x.seccion()][s];
                int b = dueDocente[x.docente()][s];
                int c = x.aula() >= 0 ? dueAula[x.aula()][s] : LIBRE;
                if (a == FIJO || b == FIJO || c == FIJO) continue; // choca con una clase que no se puede mover
                int costo = distintos(a, b, c);
                if (tabu[l][s] > iter && costo > 0) continue;
                double pts = puntaje(x, s) + rng.nextDouble();
                if (costo < mejorCosto || (costo == mejorCosto && pts < mejorPts)) {
                    mejorCosto = costo;
                    mejorPts = pts;
                    mejorS = s;
                }
            }
            if (mejorS < 0) {
                continue; // no tiene ningún espacio posible (p. ej. todo fijo): queda sin colocar
            }
            for (int o : new int[] {dueSeccion[x.seccion()][mejorS], dueDocente[x.docente()][mejorS],
                    x.aula() >= 0 ? dueAula[x.aula()][mejorS] : LIBRE}) {
                if (o >= 0 && espacio[o] >= 0) {
                    tabu[o][espacio[o]] = iter + 8 + rng.nextInt(12);
                    quitar(o);
                    pendientes.add(o);
                }
            }
            colocar(l, mejorS);
            if (sinColocar < mejorSin) {
                mejorSin = sinColocar;
                mejorEstado = espacio.clone();
            }
        }
        if (sinColocar > mejorSin) {
            // volver al mejor estado visto
            for (int l = 0; l < n; l++) if (espacio[l] >= 0) quitar(l);
            for (int l = 0; l < n; l++) if (mejorEstado[l] >= 0) colocar(l, mejorEstado[l]);
        }
    }

    private int distintos(int a, int b, int c) {
        int k = 0;
        if (a >= 0) k++;
        if (b >= 0 && b != a) k++;
        if (c >= 0 && c != a && c != b) k++;
        return k;
    }
}
