package banco.transferencias.adaptador.salida.memoria;

import banco.transferencias.aplicacion.puerto.salida.RegistroDeTransferencias;
import banco.transferencias.dominio.Dinero;
import banco.transferencias.dominio.NumeroCuenta;
import banco.transferencias.dominio.Transferencia;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RegistroEnMemoria implements RegistroDeTransferencias {
    private final List<Transferencia> registros = new CopyOnWriteArrayList<>();

    @Override public void registrar(Transferencia t) { registros.add(t); }

    @Override
    public Dinero transferidoDesde(NumeroCuenta origen, LocalDate dia) {
        return registros.stream()
                .filter(t -> t.aceptada() && t.origen().equals(origen) && t.fecha().toLocalDate().equals(dia))
                .map(Transferencia::monto).reduce(Dinero.CERO, Dinero::mas);
    }

    public List<Transferencia> todas() { return List.copyOf(registros); }
}
