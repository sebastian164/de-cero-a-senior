package banco;

import org.junit.jupiter.api.Test;

/** Punto 5: ejecuta todo el código y no comprueba nada. Mídele la cobertura y el puntaje de mutación y compáralos con los de las pruebas del taller. */
class SinAsercionesCuentasTest {

    @Test
    void recorreTodo() {
        Cuentas cuentas = new Cuentas();
        cuentas.transferir("A", "B", 40_000);
        intentar(() -> cuentas.transferir("A", "B", 500_000));
        intentar(() -> cuentas.transferir("A", "X", 10));
        intentar(() -> cuentas.transferir("A", "A", 10));
        intentar(() -> cuentas.transferir("A", "B", 0));
        cuentas.saldos();
    }

    private static void intentar(Runnable accion) {
        try {
            accion.run();
        } catch (RuntimeException e) {
            // "si lanza, bien"
        }
    }
}
