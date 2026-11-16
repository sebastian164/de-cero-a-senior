package patrones.eventsourcing;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Agregado con Event Sourcing: el estado se deriva de los eventos. */
public class CuentaBancaria {

    public sealed interface Evento permits CuentaAbierta, Depositado, Retirado {
        LocalDate fecha();
    }
    public record CuentaAbierta(String numero, long saldoInicial, LocalDate fecha) implements Evento { }
    public record Depositado(long monto, LocalDate fecha) implements Evento { }
    public record Retirado(long monto, LocalDate fecha) implements Evento { }

    private String numero;
    private long saldo;
    private final List<Evento> nuevos = new ArrayList<>();

    private CuentaBancaria() { }

    public static CuentaBancaria abrir(String numero, long saldoInicial, LocalDate fecha) {
        CuentaBancaria c = new CuentaBancaria();
        c.registrar(new CuentaAbierta(numero, saldoInicial, fecha));
        return c;
    }

    /** Reconstruye reproduciendo la historia. */
    public static CuentaBancaria desde(List<Evento> historia) {
        CuentaBancaria c = new CuentaBancaria();
        historia.forEach(c::aplicar);
        return c;
    }

    /** Saldo en cualquier fecha pasada: se reproducen solo los eventos hasta ese día. */
    public static long saldoAl(List<Evento> historia, LocalDate fecha) {
        return desde(historia.stream().filter(e -> !e.fecha().isAfter(fecha)).toList()).saldo;
    }

    public void depositar(long monto, LocalDate fecha) {
        if (monto <= 0) throw new IllegalArgumentException("monto inválido");
        registrar(new Depositado(monto, fecha));
    }

    public void retirar(long monto, LocalDate fecha) {
        if (monto > saldo) throw new IllegalStateException("Saldo insuficiente");
        registrar(new Retirado(monto, fecha));
    }

    private void registrar(Evento e) {
        aplicar(e);
        nuevos.add(e);
    }

    private void aplicar(Evento e) {
        switch (e) {
            case CuentaAbierta a -> { numero = a.numero(); saldo = a.saldoInicial(); }
            case Depositado d -> saldo += d.monto();
            case Retirado r -> saldo -= r.monto();
        }
    }

    public List<Evento> eventosNuevos() { return List.copyOf(nuevos); }
    public long saldo() { return saldo; }
    public String numero() { return numero; }
}
