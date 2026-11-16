package taller.command;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        Banco banco = new Banco();
        banco.abrir("A", 100_000);
        banco.abrir("B", 0);
        Historial h = new Historial();

        h.ejecutar(new Depositar(banco, "A", 50_000));
        h.ejecutar(new Transferir(banco, "A", "B", 30_000));
        System.out.println("Tras depositar y transferir: " + banco);
        h.deshacer();
        System.out.println("Deshacer transferencia:     " + banco);
        h.rehacer();
        System.out.println("Rehacer:                    " + banco);

        try {
            h.ejecutar(new Macro(List.of(new Depositar(banco, "B", 10_000), new Retirar(banco, "A", 1_000_000))));
        } catch (FondosInsuficientesException e) {
            System.out.println("Macro falló (" + e.getMessage() + "), estado intacto: " + banco);
        }
        h.ejecutar(new Macro(List.of(new Retirar(banco, "A", 20_000), new Depositar(banco, "B", 20_000))));
        System.out.println("Macro exitoso:              " + banco);
        h.deshacer();
        System.out.println("Macro deshecho completo:    " + banco);

        System.out.println(ComandoVsEvento.ejemplo());
    }
}

final class FondosInsuficientesException extends RuntimeException {
    FondosInsuficientesException(String cuenta, long saldo, long monto) {
        super("Fondos insuficientes en " + cuenta + ": saldo " + saldo + ", se pidió " + monto);
    }
}

/** El receptor: sabe hacer las operaciones, no sabe nada de deshacer. */
final class Banco {
    private final Map<String, Long> saldos = new LinkedHashMap<>();
    void abrir(String cuenta, long saldo) { saldos.put(cuenta, saldo); }
    long saldo(String cuenta) { return saldos.get(cuenta); }
    void depositar(String cuenta, long monto) { saldos.merge(cuenta, monto, Long::sum); }
    void retirar(String cuenta, long monto) {
        long saldo = saldos.get(cuenta);
        if (saldo < monto) throw new FondosInsuficientesException(cuenta, saldo, monto);
        saldos.put(cuenta, saldo - monto);
    }
    @Override public String toString() { return saldos.toString(); }
}

interface Comando {
    void ejecutar();
    void deshacer();
}

record Depositar(Banco banco, String cuenta, long monto) implements Comando {
    public void ejecutar() { banco.depositar(cuenta, monto); }
    public void deshacer() { banco.retirar(cuenta, monto); }
}

record Retirar(Banco banco, String cuenta, long monto) implements Comando {
    public void ejecutar() { banco.retirar(cuenta, monto); }
    public void deshacer() { banco.depositar(cuenta, monto); }
}

record Transferir(Banco banco, String origen, String destino, long monto) implements Comando {
    public void ejecutar() {
        banco.retirar(origen, monto);       // si falla, no se tocó el destino
        banco.depositar(destino, monto);
    }
    public void deshacer() {
        banco.retirar(destino, monto);
        banco.depositar(origen, monto);
    }
}

/** Paso 3 y 4: macro atómico. Si un paso falla, deshace los ya ejecutados en orden inverso y propaga. */
final class Macro implements Comando {
    private final List<Comando> pasos;
    Macro(List<Comando> pasos) { this.pasos = List.copyOf(pasos); }

    @Override public void ejecutar() {
        List<Comando> hechos = new ArrayList<>();
        try {
            for (Comando c : pasos) {
                c.ejecutar();
                hechos.add(c);
            }
        } catch (RuntimeException e) {
            for (int i = hechos.size() - 1; i >= 0; i--) hechos.get(i).deshacer();
            throw e;
        }
    }

    @Override public void deshacer() {
        for (int i = pasos.size() - 1; i >= 0; i--) pasos.get(i).deshacer();
    }
}

/** El invocador con deshacer/rehacer. */
final class Historial {
    private final Deque<Comando> hechos = new ArrayDeque<>();
    private final Deque<Comando> deshechos = new ArrayDeque<>();

    void ejecutar(Comando c) {
        c.ejecutar();             // si falla, no entra al historial
        hechos.push(c);
        deshechos.clear();        // Paso 2: un comando nuevo invalida la rama de rehacer
    }

    boolean deshacer() {
        if (hechos.isEmpty()) return false;
        Comando c = hechos.pop();
        c.deshacer();
        deshechos.push(c);
        return true;
    }

    boolean rehacer() {
        if (deshechos.isEmpty()) return false;
        Comando c = deshechos.pop();
        c.ejecutar();
        hechos.push(c);
        return true;
    }

    int pendientesDeRehacer() { return deshechos.size(); }
}

/** Paso 5. */
final class ComandoVsEvento {
    /** Comando: una intención, en imperativo, dirigida a UN destinatario, que puede rechazarse. */
    record ConfirmarPedido(String pedidoId, String solicitadoPor) { }
    /** Evento: un hecho ya ocurrido, en pasado, que se publica a quien quiera escucharlo; no se rechaza. */
    record PedidoConfirmado(String pedidoId, java.time.Instant cuando) { }

    static String ejemplo() {
        List<Object> publicados = new ArrayList<>();
        ConfirmarPedido cmd = new ConfirmarPedido("P-1", "ana");
        boolean pagado = true;                                  // el manejador valida y puede rechazar
        if (pagado) publicados.add(new PedidoConfirmado(cmd.pedidoId(), java.time.Instant.EPOCH));
        return "Comando " + cmd + " -> eventos " + publicados;
    }
}
