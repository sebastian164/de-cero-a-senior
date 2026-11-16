package mensajeria.despacho.dominio;

import java.util.Map;

/** Precio = (base + km × tarifa + recargo por tamaño) × factor de franja × (1 − descuento negociado). */
public class Cotizador {
    public static final long BASE = 4_000;
    public static final long POR_KM = 1_200;

    public sealed interface TipoCliente permits Persona, Comercio { }
    public record Persona() implements TipoCliente { }
    public record Comercio(String id) implements TipoCliente { }

    private final Map<String, Integer> descuentoNegociadoPorcentaje;

    public Cotizador(Map<String, Integer> descuentoNegociadoPorcentaje) {
        this.descuentoNegociadoPorcentaje = Map.copyOf(descuentoNegociadoPorcentaje);
    }

    public long cotizar(Punto origen, Punto destino, Tamano tamano, Franja franja, TipoCliente cliente) {
        double km = origen.distanciaA(destino);
        double bruto = BASE + km * POR_KM + tamano.recargo();
        if (franja.esHoraPico()) bruto *= 1.25;
        int descuento = switch (cliente) {
            case Persona p -> 0;
            case Comercio c -> descuentoNegociadoPorcentaje.getOrDefault(c.id(), 0);
        };
        long sinRedondeo = Math.round(bruto * (100 - descuento) / 100.0);
        return (sinRedondeo + 99) / 100 * 100;      // redondeo hacia arriba a la centena
    }
}
