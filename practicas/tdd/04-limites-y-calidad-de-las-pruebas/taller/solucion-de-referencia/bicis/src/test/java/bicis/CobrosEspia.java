package bicis;

import java.util.ArrayList;
import java.util.List;

/** Espía: anota los pagos registrados. Aquí la llamada ES el resultado que se quiere comprobar. */
final class CobrosEspia implements Cobros {

    record Cobro(String socio, int valor) {
    }

    final List<Cobro> registrados = new ArrayList<>();

    @Override
    public void registrar(String socio, int valor) {
        registrados.add(new Cobro(socio, valor));
    }
}
