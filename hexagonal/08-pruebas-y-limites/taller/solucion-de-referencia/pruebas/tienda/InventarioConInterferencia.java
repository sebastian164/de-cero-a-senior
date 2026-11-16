package tienda;

import java.util.Optional;
import tienda.aplicacion.puertos.salida.Inventario;
import tienda.dominio.Existencias;

/** Envuelve un inventario real y, justo después de una lectura, deja actuar a otro hilo: otra transacción se adelanta. */
public final class InventarioConInterferencia implements Inventario {

    private final Inventario real;
    private Runnable interferencia;
    private int vecesPendientes;
    private int lecturas;

    public InventarioConInterferencia(Inventario real) {
        this.real = real;
    }

    public void interferir(int veces, Runnable interferencia) {
        this.vecesPendientes = veces;
        this.interferencia = interferencia;
        this.lecturas = 0;
    }

    public int lecturas() {
        return lecturas;
    }

    @Override
    public Optional<Existencias> existenciasDe(String producto) {
        Optional<Existencias> leidas = real.existenciasDe(producto);
        if (Thread.currentThread().getName().equals("el-otro")) {
            return leidas;
        }
        lecturas++;
        if (vecesPendientes > 0) {
            vecesPendientes--;
            Thread otro = new Thread(interferencia, "el-otro");
            otro.start();
            try {
                otro.join();
            } catch (InterruptedException interrupcion) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupcion);
            }
        }
        return leidas;                                           // lo leído ya es viejo
    }

    @Override
    public void guardar(Existencias existencias) {
        real.guardar(existencias);
    }
}
