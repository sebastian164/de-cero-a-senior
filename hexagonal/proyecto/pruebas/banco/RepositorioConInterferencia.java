package banco;

import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import java.util.Optional;

/** Envuelve un repositorio real y, justo después de una lectura, deja actuar a otro hilo. */
public final class RepositorioConInterferencia implements RepositorioDeCuentas {

    private final RepositorioDeCuentas real;
    private Runnable interferencia;
    private int vecesPendientes;
    private int lecturas;

    public RepositorioConInterferencia(RepositorioDeCuentas real) {
        this.real = real;
    }

    /** Lo que hará "el otro" después de cada una de las próximas lecturas, tantas veces como se indique. */
    public void interferir(int veces, Runnable interferencia) {
        this.vecesPendientes = veces;
        this.interferencia = interferencia;
        this.lecturas = 0;
    }

    public int lecturas() {
        return lecturas;
    }

    @Override
    public String siguienteId() {
        return real.siguienteId();
    }

    @Override
    public void guardar(Cuenta cuenta) {
        real.guardar(cuenta);
    }

    @Override
    public Optional<Cuenta> buscar(String id) {
        Optional<Cuenta> leida = real.buscar(id);
        if (Thread.currentThread().getName().equals("el-otro")) {
            return leida;                                        // el otro no se interfiere a sí mismo
        }
        lecturas++;
        if (vecesPendientes > 0) {
            vecesPendientes--;
            enOtroHilo(interferencia);                           // otro hilo es otra transacción
        }
        return leida;                                            // lo leído ya es viejo
    }

    private static void enOtroHilo(Runnable accion) {
        Thread otro = new Thread(accion, "el-otro");
        otro.start();
        try {
            otro.join();
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupcion);
        }
    }
}
