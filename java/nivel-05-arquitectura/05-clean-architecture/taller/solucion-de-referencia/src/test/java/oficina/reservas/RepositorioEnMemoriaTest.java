package oficina.reservas;

import oficina.reservas.adaptadores.persistencia.memoria.RepositorioReservasEnMemoria;
import oficina.reservas.aplicacion.puertos.RepositorioReservas;

class RepositorioEnMemoriaTest extends ContratoRepositorioReservas {
    private final RepositorioReservas repo = new RepositorioReservasEnMemoria();
    @Override RepositorioReservas repositorio() { return repo; }
}
