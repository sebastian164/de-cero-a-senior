package banco;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Lo que cualquier adaptador de RepositorioDeCuentas debe cumplir. Se escribe una vez y se ejecuta contra cada uno. */
public final class ContratoDelRepositorio {

    private static final LocalDate HOY = LocalDate.parse("2026-11-15");

    private ContratoDelRepositorio() {
    }

    /** Devuelve las cláusulas que el adaptador incumple. Vacío significa que cumple el contrato. */
    public static List<String> incumplimientos(Supplier<RepositorioDeCuentas> fabrica) {
        List<String> incumplidas = new ArrayList<>();

        RepositorioDeCuentas cuentas = fabrica.get();
        String id = cuentas.siguienteId();
        cuentas.guardar(Cuenta.abrir(id, "Ana", Dinero.pesos(1_000), HOY));

        Cuenta leida = cuentas.buscar(id).orElse(null);
        if (leida == null || !leida.titular().equals("Ana") || leida.saldo().centavos() != 100_000) {
            incumplidas.add("lo guardado se recupera igual");
            return incumplidas;
        }
        if (cuentas.buscar("no-existe").isPresent()) {
            incumplidas.add("buscar algo que no existe devuelve vacío");
        }
        if (cuentas.siguienteId().equals(id)) {
            incumplidas.add("los identificadores no se repiten");
        }

        leida.depositar(Dinero.pesos(500));                              // se modifica y NO se guarda
        if (cuentas.buscar(id).orElseThrow().saldo().centavos() != 100_000) {
            incumplidas.add("lo que se entrega es una copia");
        }

        Cuenta unaLectura = cuentas.buscar(id).orElseThrow();
        Cuenta otraLectura = cuentas.buscar(id).orElseThrow();
        unaLectura.depositar(Dinero.pesos(1));
        cuentas.guardar(unaLectura);
        otraLectura.depositar(Dinero.pesos(2));
        try {
            cuentas.guardar(otraLectura);                                // ya es vieja
            incumplidas.add("guardar con una versión vieja falla con conflicto");
        } catch (ConflictoDeConcurrencia esperado) {
            // cumple
        }
        return incumplidas;
    }
}
