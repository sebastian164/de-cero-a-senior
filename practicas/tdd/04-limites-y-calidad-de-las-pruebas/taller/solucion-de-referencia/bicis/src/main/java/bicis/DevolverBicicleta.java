package bicis;

import java.time.Duration;
import java.time.LocalDateTime;

/** Caso de uso: devolver una bicicleta. */
public final class DevolverBicicleta {

    private final Alquileres alquileres;
    private final Bicicletas bicicletas;
    private final Cobros cobros;
    private final Reloj reloj;

    public DevolverBicicleta(Alquileres alquileres, Bicicletas bicicletas, Cobros cobros, Reloj reloj) {
        this.alquileres = alquileres;
        this.bicicletas = bicicletas;
        this.cobros = cobros;
        this.reloj = reloj;
    }

    /** Cierra el alquiler, registra el pago si hay algo que cobrar y deja la bicicleta en la estación de destino. */
    public int ejecutar(String idAlquiler, String estacionDestino) {
        // La hora se lee primero: es un refactor sin efecto externo (ni el reloj ni la búsqueda cambian nada), solo cambia el orden de las lecturas.
        LocalDateTime fin = reloj.ahora();
        Alquiler alquiler = abierto(idAlquiler);
        int valor = cobrable(alquiler, fin, estacionDestino);
        if (valor > 0) {
            cobros.registrar(alquiler.socio(), valor);
        }
        alquileres.guardar(alquiler.cerrar());
        bicicletas.dejarDisponible(alquiler.bicicleta(), estacionDestino);
        return valor;
    }

    private Alquiler abierto(String idAlquiler) {
        return alquileres.buscar(idAlquiler)
                .filter(alquiler -> !alquiler.cerrado())
                .orElseThrow(() -> new AlquilerNoDisponible(idAlquiler));
    }

    private static int cobrable(Alquiler alquiler, LocalDateTime fin, String estacionDestino) {
        return Tarifa.precio(Duration.between(alquiler.inicio(), fin), alquiler.abonado())
                + Tarifa.recargo(alquiler.estacionOrigen(), estacionDestino);
    }
}
