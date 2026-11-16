package academia;

import org.junit.jupiter.api.Test;

/** Punto 5: ejecuta todo el código y no comprueba nada. */
class SinAsercionesCursoTest {

    @Test
    void recorreTodo() {
        intentar(() -> new Curso(null, 1));
        intentar(() -> new Curso(" ", 1));
        intentar(() -> new Curso("Java", 0));
        Curso curso = new Curso("Java", 1);
        curso.inscribir("Ana");
        intentar(() -> curso.inscribir("Ana"));
        intentar(() -> curso.inscribir("Luis"));
        curso.nombre();
        curso.estudiantes();
    }

    private static void intentar(Runnable accion) {
        try {
            accion.run();
        } catch (RuntimeException e) {
            // "si lanza, bien"
        }
    }
}
