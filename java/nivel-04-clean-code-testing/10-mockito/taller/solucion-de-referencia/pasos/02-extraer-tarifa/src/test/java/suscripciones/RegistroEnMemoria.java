package suscripciones;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Fake de RegistroRenovaciones: guarda en una lista lo que se registró, con la misma forma de la línea del log. */
class RegistroEnMemoria implements RegistroRenovaciones {

    record Linea(LocalDate fecha, String suscriptor, long valorEnPesos, String comprobante) {
    }

    private final List<Linea> lineas = new ArrayList<>();

    List<Linea> lineas() {
        return List.copyOf(lineas);
    }

    @Override
    public void registrar(LocalDate fecha, String suscriptor, long valorEnPesos, Comprobante comprobante) {
        lineas.add(new Linea(fecha, suscriptor, valorEnPesos, comprobante.codigo()));
    }
}
