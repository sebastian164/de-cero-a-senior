import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class Main {

    public static void main(String[] args) {
        ValidadorPedido validador = new ValidadorPedido(List.of(
                new ReglaClienteObligatorio(),
                new ReglaTieneItems(),
                new ReglaValorMaximo(5_000_000),
                new ReglaCobertura(Set.of("Leticia", "San Andrés"))));

        System.out.println(validador.validar(new Pedido("Ana", 3, 250_000, "Tunja")));
        System.out.println(validador.validar(new Pedido("", 0, 9_000_000, "Leticia")));
    }
}

class Pedido {
    final String cliente;
    final int cantidadItems;
    final long valor;
    final String ciudad;

    Pedido(String cliente, int cantidadItems, long valor, String ciudad) {
        this.cliente = cliente;
        this.cantidadItems = cantidadItems;
        this.valor = valor;
        this.ciudad = ciudad;
    }
}

/** El punto de extensión: la "toma" donde se enchufan reglas nuevas. */
interface ReglaValidacion {
    Optional<String> evaluar(Pedido pedido);
}

/** Cerrado: una regla nueva no obliga a modificar esta clase. */
class ValidadorPedido {

    private final List<ReglaValidacion> reglas;

    ValidadorPedido(List<ReglaValidacion> reglas) {
        this.reglas = List.copyOf(reglas);
    }

    List<String> validar(Pedido pedido) {
        List<String> errores = new ArrayList<>();
        for (ReglaValidacion regla : reglas) {
            regla.evaluar(pedido).ifPresent(errores::add);
        }
        return errores;
    }
}

class ReglaClienteObligatorio implements ReglaValidacion {
    @Override
    public Optional<String> evaluar(Pedido pedido) {
        boolean falta = pedido.cliente == null || pedido.cliente.isBlank();
        return falta ? Optional.of("El pedido no tiene cliente") : Optional.empty();
    }
}

class ReglaTieneItems implements ReglaValidacion {
    @Override
    public Optional<String> evaluar(Pedido pedido) {
        return pedido.cantidadItems <= 0 ? Optional.of("El pedido no tiene ítems") : Optional.empty();
    }
}

class ReglaValorMaximo implements ReglaValidacion {

    private final long maximo;

    ReglaValorMaximo(long maximo) {
        this.maximo = maximo;
    }

    @Override
    public Optional<String> evaluar(Pedido pedido) {
        return pedido.valor > maximo
                ? Optional.of("El valor supera el máximo permitido") : Optional.empty();
    }
}

class ReglaCobertura implements ReglaValidacion {

    private final Set<String> ciudadesSinCobertura;

    ReglaCobertura(Set<String> ciudadesSinCobertura) {
        this.ciudadesSinCobertura = ciudadesSinCobertura;
    }

    @Override
    public Optional<String> evaluar(Pedido pedido) {
        return ciudadesSinCobertura.contains(pedido.ciudad)
                ? Optional.of("No hay cobertura de envío a " + pedido.ciudad) : Optional.empty();
    }
}
