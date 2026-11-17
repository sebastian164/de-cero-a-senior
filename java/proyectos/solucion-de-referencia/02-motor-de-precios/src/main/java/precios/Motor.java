package precios;

import precios.Modelo.*;

import java.math.BigDecimal;
import java.util.*;

import static precios.Modelo.*;

public class Motor {
    /** El orden de aplicación está definido en un solo lugar: esta lista. */
    private final List<Promocion> promociones;
    private final Map<String, BigDecimal> impuestoPorCategoria;
    private final BigDecimal costoEnvio;
    private final BigDecimal envioGratisDesde;

    public Motor(List<Promocion> promociones, Map<String, BigDecimal> impuestoPorCategoria, BigDecimal costoEnvio, BigDecimal envioGratisDesde) {
        this.promociones = List.copyOf(promociones);
        this.impuestoPorCategoria = Map.copyOf(impuestoPorCategoria);
        this.costoEnvio = costoEnvio;
        this.envioGratisDesde = envioGratisDesde;
    }

    public Resultado calcular(Carrito carrito) {
        Map<Producto, Integer> unidades = new LinkedHashMap<>();
        Map<Producto, BigDecimal> valores = new LinkedHashMap<>();       // lo que vale cada producto en el carrito
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Linea l : carrito.lineas()) {
            l.item().aplanar(l.cantidad(), unidades);
            l.item().repartir(precioDe(l.item()), l.cantidad(), valores);
            subtotal = subtotal.add(precioDe(l.item()).multiply(BigDecimal.valueOf(l.cantidad())));
        }
        subtotal = redondear(subtotal);

        BigDecimal base = subtotal;
        List<Descuento> aplicados = new ArrayList<>();
        Map<String, Integer> posicionDelGrupo = new HashMap<>();
        for (Promocion p : promociones) {
            Optional<Descuento> d = p.evaluar(new Contexto(unidades, valores, carrito.cupones(), base));
            if (d.isEmpty()) continue;
            if (p.grupo() != null) {
                Integer pos = posicionDelGrupo.get(p.grupo());
                if (pos != null) { // excluyente: se queda la que más descuenta
                    if (d.get().monto().compareTo(aplicados.get(pos).monto()) <= 0) continue;
                    base = base.add(aplicados.get(pos).monto());
                    aplicados.remove((int) pos);
                    posicionDelGrupo.replaceAll((g, i) -> i > pos ? i - 1 : i);
                }
                posicionDelGrupo.put(p.grupo(), aplicados.size());
            }
            Descuento aplicar = new Descuento(d.get().promocion(), d.get().monto().min(base), d.get().detalle());
            aplicados.add(aplicar);
            base = base.subtract(aplicar.monto());
        }

        BigDecimal descontado = subtotal.subtract(base);
        BigDecimal factor = subtotal.signum() == 0 ? BigDecimal.ONE : base.divide(subtotal, 10, java.math.RoundingMode.HALF_UP);
        BigDecimal impuestos = BigDecimal.ZERO;
        for (var e : valores.entrySet()) { // el descuento se reparte proporcionalmente entre categorías
            BigDecimal bruto = e.getValue();                              // para un combo: la parte del precio del combo, no el precio de lista
            impuestos = impuestos.add(bruto.multiply(factor).multiply(impuestoPorCategoria.getOrDefault(e.getKey().categoria(), BigDecimal.ZERO)));
        }
        impuestos = redondear(impuestos);

        boolean gratis = base.compareTo(envioGratisDesde) >= 0;
        BigDecimal envio = gratis ? BigDecimal.ZERO : costoEnvio;
        if (gratis) aplicados.add(new Descuento("Envío gratis", BigDecimal.ZERO.setScale(2), "compra desde " + envioGratisDesde));
        return new Resultado(subtotal, List.copyOf(aplicados), envio, impuestos, redondear(base.add(impuestos).add(envio)));
    }

    private BigDecimal precioDe(Item i) {
        return switch (i) {
            case Producto p -> p.precio();
            case Combo c -> c.precio();
        };
    }
}
