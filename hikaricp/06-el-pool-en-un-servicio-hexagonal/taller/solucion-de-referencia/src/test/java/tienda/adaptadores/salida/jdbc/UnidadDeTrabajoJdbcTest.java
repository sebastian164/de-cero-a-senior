package tienda.adaptadores.salida.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Paso 3 del taller 06: el adaptador de transacciones, sin base de datos y con conexiones falsas. */
class UnidadDeTrabajoJdbcTest {

    private final ConexionesFalsas falsas = new ConexionesFalsas();
    private final UnidadDeTrabajoJdbc transacciones = new UnidadDeTrabajoJdbc(falsas.dataSource());

    @Test
    void confirmaSiTodoVaBien() {
        assertEquals("listo", transacciones.ejecutar(() -> "listo"));
        assertEquals(List.of("getConnection", "setAutoCommit", "commit", "close"), falsas.eventos);
    }

    @Test
    void revierteSiAlgoFalla() {
        assertThrows(IllegalArgumentException.class, () -> transacciones.ejecutar(() -> {
            throw new IllegalArgumentException("regla violada");
        }));
        assertEquals(List.of("getConnection", "setAutoCommit", "rollback", "close"), falsas.eventos);
    }

    @Test
    void devuelveLaConexionEnTodosLosCaminos() {
        transacciones.ejecutar(() -> 1);
        assertThrows(IllegalStateException.class, () -> transacciones.ejecutar(() -> {
            throw new IllegalStateException("falla");
        }));
        assertThrows(Error.class, () -> transacciones.ejecutar(() -> {
            throw new AssertionError("ni un Error la deja fuera");
        }));
        assertEquals(falsas.pedidas, falsas.cerradas);
    }

    @Test
    void unaUnidadAnidadaNoPideOtraConexion() {
        transacciones.ejecutar(() -> transacciones.ejecutar(() -> transacciones.ejecutar(() -> 1)));
        assertEquals(1, falsas.pedidas);
        assertEquals(1, falsas.cerradas);
        assertEquals(1, falsas.eventos.stream().filter("commit"::equals).count());
    }

    @Test
    void despuesDeUnaUnidadNoQuedaNingunaConexionEnCurso() {
        transacciones.ejecutar(() -> 1);
        transacciones.ejecutar(() -> 2);
        assertEquals(2, falsas.pedidas);
    }

    @Test
    void siFallaDentroDeUnaAnidadaSeRevierteTodoUnaSolaVez() {
        assertThrows(IllegalStateException.class, () -> transacciones.ejecutar(() -> transacciones.ejecutar(() -> {
            throw new IllegalStateException("falla la interna");
        })));
        assertEquals(List.of("getConnection", "setAutoCommit", "rollback", "close"), falsas.eventos);
    }

    @Test
    void siElRollbackFallaSeConservaElErrorOriginal() {
        ConexionesFalsas rota = new ConexionesFalsas();
        rota.fallarAlRevertir = true;
        UnidadDeTrabajoJdbc unidad = new UnidadDeTrabajoJdbc(rota.dataSource());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> unidad.ejecutar(() -> {
            throw new IllegalArgumentException("regla violada");
        }));
        assertEquals("regla violada", error.getMessage());
        assertEquals(1, error.getSuppressed().length);
        assertEquals(rota.pedidas, rota.cerradas);
    }
}
