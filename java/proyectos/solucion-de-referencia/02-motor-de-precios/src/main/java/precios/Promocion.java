package precios;

import precios.Modelo.*;

import java.math.BigDecimal;
import java.util.Optional;

import static precios.Modelo.*;

/** Una promoción es una clase. El orden y las exclusiones no viven aquí, viven en {@link Motor}. */
public interface Promocion {
    String nombre();

    /** Grupo excluyente: dentro de un mismo grupo solo aplica la que más descuenta. Nulo = se acumula con todo. */
    default String grupo() { return null; }

    /** Descuento sobre el estado actual del carrito (ya con los descuentos anteriores restados). */
    Optional<Descuento> evaluar(Contexto c);

    record Porcentaje(String nombre, BigDecimal pct, String grupo) implements Promocion {
        public Optional<Descuento> evaluar(Contexto c) {
            return Optional.of(new Descuento(nombre, redondear(c.base().multiply(pct).divide(d("100"))), pct + "% sobre " + c.base()));
        }
    }

    record ValorFijo(String nombre, BigDecimal monto, String grupo) implements Promocion {
        public Optional<Descuento> evaluar(Contexto c) {
            BigDecimal m = monto.min(c.base());
            return m.signum() == 0 ? Optional.empty() : Optional.of(new Descuento(nombre, m, "valor fijo"));
        }
    }

    record DosPorUno(String nombre, String producto) implements Promocion {
        public Optional<Descuento> evaluar(Contexto c) {
            return c.unidades().entrySet().stream().filter(e -> e.getKey().nombre().equals(producto)).findFirst()
                    .filter(e -> e.getValue() >= 2)
                    .map(e -> new Descuento(nombre, redondear(c.precioUnitario(e.getKey()).multiply(BigDecimal.valueOf(e.getValue() / 2))),
                            e.getValue() / 2 + " unidad(es) gratis de " + producto));
        }
    }

    record Volumen(String nombre, String producto, int minimo, BigDecimal pct) implements Promocion {
        public Optional<Descuento> evaluar(Contexto c) {
            return c.unidades().entrySet().stream().filter(e -> e.getKey().nombre().equals(producto) && e.getValue() >= minimo).findFirst()
                    .map(e -> new Descuento(nombre, redondear(c.precioUnitario(e.getKey()).multiply(BigDecimal.valueOf(e.getValue())).multiply(pct).divide(d("100"))),
                            pct + "% por llevar " + e.getValue() + " (mínimo " + minimo + ")"));
        }
    }

    record Cupon(String nombre, String codigo, BigDecimal pct) implements Promocion {
        public String grupo() { return "cupon"; }
        public Optional<Descuento> evaluar(Contexto c) {
            return c.cupones().contains(codigo)
                    ? Optional.of(new Descuento(nombre, redondear(c.base().multiply(pct).divide(d("100"))), "cupón " + codigo))
                    : Optional.empty();
        }
    }
}
