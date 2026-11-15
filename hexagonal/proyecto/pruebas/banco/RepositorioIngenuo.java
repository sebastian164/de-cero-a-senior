package banco;

import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Un repositorio que guarda lo que le den, sin mirar la versión: la última escritura gana. */
public final class RepositorioIngenuo implements RepositorioDeCuentas {

    private final Map<String, Cuenta> cuentas = new HashMap<>();
    private int secuencia = 0;

    @Override
    public String siguienteId() {
        return "cuenta-" + (++secuencia);
    }

    @Override
    public void guardar(Cuenta cuenta) {
        cuentas.put(cuenta.id(), copia(cuenta));
    }

    @Override
    public Optional<Cuenta> buscar(String id) {
        return Optional.ofNullable(cuentas.get(id)).map(RepositorioIngenuo::copia);
    }

    private static Cuenta copia(Cuenta cuenta) {
        return Cuenta.existente(cuenta.id(), cuenta.titular(), cuenta.saldo(), cuenta.enviadoHoy(), cuenta.diaDelTope(), cuenta.version());
    }
}
