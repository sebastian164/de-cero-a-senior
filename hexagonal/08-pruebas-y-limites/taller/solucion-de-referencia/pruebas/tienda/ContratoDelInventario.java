package tienda;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.puertos.salida.Inventario;
import tienda.aplicacion.puertos.salida.UnidadDeTrabajo;
import tienda.dominio.Existencias;

/** Lo que cualquier adaptador de Inventario debe cumplir. */
public final class ContratoDelInventario {

    /** El producto lo da quien arma el escenario: en una base real debe existir en el catálogo. */
    public record Escenario(Inventario inventario, UnidadDeTrabajo unidadDeTrabajo, String producto) {
    }

    private ContratoDelInventario() {
    }

    public static List<String> incumplimientos(Supplier<Escenario> fabrica) {
        List<String> incumplidas = new ArrayList<>();
        Escenario escenario = fabrica.get();
        Inventario inventario = escenario.inventario();
        UnidadDeTrabajo transaccion = escenario.unidadDeTrabajo();
        String producto = escenario.producto();

        ContratoDelRepositorioDePedidos.enTransaccion(transaccion, () -> inventario.guardar(Existencias.de(producto, 10)));
        if (unidades(escenario) != 10) {
            incumplidas.add("lo guardado se recupera igual");
            return incumplidas;
        }
        if (transaccion.ejecutar(() -> inventario.existenciasDe("no-existe-" + UUID.randomUUID())).isPresent()) {
            incumplidas.add("un producto sin existencias registradas devuelve vacío");
        }

        transaccion.ejecutar(() -> inventario.existenciasDe(producto)).orElseThrow().reservar(4);   // NO se guarda
        if (unidades(escenario) != 10) {
            incumplidas.add("lo que se entrega es una copia");
        }

        ContratoDelRepositorioDePedidos.enTransaccion(transaccion, () -> {
            Existencias leidas = inventario.existenciasDe(producto).orElseThrow();
            leidas.reservar(3);
            inventario.guardar(leidas);
        });
        if (unidades(escenario) != 7) {
            incumplidas.add("guardar lo leído en la misma transacción actualiza");
        }

        try {
            ContratoDelRepositorioDePedidos.enTransaccion(transaccion, () -> {
                Existencias leidas = inventario.existenciasDe(producto).orElseThrow();
                leidas.reservar(7);
                inventario.guardar(leidas);
                throw new IllegalStateException("se revierte");
            });
        } catch (IllegalStateException esperado) {
            // revertida
        }
        if (unidades(escenario) != 7) {
            incumplidas.add("lo guardado en una transacción revertida no queda");
        }

        ContratoDelRepositorioDePedidos.enTransaccion(transaccion, () -> inventario.guardar(Existencias.de(producto, 7)));
        try {
            ContratoDelRepositorioDePedidos.enTransaccion(transaccion, () -> {
                Existencias mias = inventario.existenciasDe(producto).orElseThrow();
                ContratoDelRepositorioDePedidos.enOtroHilo(() -> ContratoDelRepositorioDePedidos.enTransaccion(transaccion, () -> {
                    Existencias suyas = inventario.existenciasDe(producto).orElseThrow();
                    suyas.reservar(1);
                    inventario.guardar(suyas);
                }));
                mias.reservar(2);
                inventario.guardar(mias);
            });
            incumplidas.add("guardar lo que se leyó antes de que otro lo cambiara falla con conflicto");
        } catch (ConflictoDeConcurrencia esperado) {
            // cumple
        }
        if (unidades(escenario) != 6) {
            incumplidas.add("tras el conflicto queda solo el cambio de quien confirmó primero");
        }
        return incumplidas;
    }

    private static int unidades(Escenario escenario) {
        return escenario.unidadDeTrabajo().ejecutar(() -> escenario.inventario().existenciasDe(escenario.producto())).orElseThrow().unidades();
    }
}
