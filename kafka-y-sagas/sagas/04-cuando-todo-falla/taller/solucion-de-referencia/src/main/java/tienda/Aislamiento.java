package tienda;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Paso 7: varias sagas compran a la vez la última unidad. Cada saga tiene tres pasos: consultar existencias,
 * cobrar y descontar. Un planificador intercala los pasos con una semilla: la misma semilla, el mismo intercalado.
 */
public final class Aislamiento {

    public enum Estrategia { INGENUA, BLOQUEO_SEMANTICO, VERSION }

    public record Resultado(int vendidas, int existenciasFinales, int cobrosVigentes, int reembolsos) {
        public boolean dobleVenta(int stock) {
            return vendidas > stock || existenciasFinales < 0 || cobrosVigentes != vendidas;
        }
    }

    private static final class Inventario {
        int existencias;
        int apartadas;          // bloqueo semántico: unidades comprometidas por sagas en curso
        long version;
    }

    private static final class Compra {
        int paso;
        long versionLeida;
        boolean cobrada;
        boolean vendida;
        boolean cancelada;
    }

    public static Resultado vender(int stock, int compradores, Estrategia estrategia, long semilla) {
        Inventario inv = new Inventario();
        inv.existencias = stock;
        List<Compra> compras = new ArrayList<>();
        for (int i = 0; i < compradores; i++) {
            compras.add(new Compra());
        }
        Random planificador = new Random(semilla);
        int reembolsos = 0;
        List<Compra> vivas = new ArrayList<>(compras);
        while (!vivas.isEmpty()) {
            Compra c = vivas.get(planificador.nextInt(vivas.size()));
            switch (c.paso++) {
                case 0 -> {                                   // consultar
                    boolean hay = switch (estrategia) {
                        case INGENUA -> inv.existencias > 0;
                        case BLOQUEO_SEMANTICO -> {
                            boolean libre = inv.existencias - inv.apartadas > 0;
                            if (libre) {
                                inv.apartadas++;              // la unidad queda "pendiente": nadie más la ve
                            }
                            yield libre;
                        }
                        case VERSION -> {
                            c.versionLeida = inv.version;
                            yield inv.existencias > 0;
                        }
                    };
                    if (!hay) {
                        c.cancelada = true;
                    }
                }
                case 1 -> c.cobrada = true;                   // cobrar
                case 2 -> {                                   // descontar
                    boolean ok = switch (estrategia) {
                        case INGENUA -> true;
                        case BLOQUEO_SEMANTICO -> {
                            inv.apartadas--;
                            yield true;                       // la unidad ya estaba comprometida para esta saga
                        }
                        case VERSION -> inv.version == c.versionLeida && inv.existencias > 0;
                    };
                    if (ok) {
                        inv.existencias--;
                        inv.version++;
                        c.vendida = true;
                    } else {
                        c.cobrada = false;                    // compensar: reembolsar
                        reembolsos++;
                        c.cancelada = true;
                    }
                }
                default -> { }
            }
            if (c.cancelada || c.paso == 3) {
                vivas.remove(c);
            }
        }
        int vendidas = (int) compras.stream().filter(x -> x.vendida).count();
        int cobros = (int) compras.stream().filter(x -> x.cobrada).count();
        return new Resultado(vendidas, inv.existencias, cobros, reembolsos);
    }
}
