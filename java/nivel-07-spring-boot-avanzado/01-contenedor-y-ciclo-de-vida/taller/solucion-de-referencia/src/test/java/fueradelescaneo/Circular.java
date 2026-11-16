package fueradelescaneo;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Pedidos necesita a Facturacion y Facturacion necesita a Pedidos. */
public final class Circular {
    private Circular() { }

    public static class Pedidos { public Pedidos(Facturacion f) { } }
    public static class Facturacion { public Facturacion(Pedidos p) { } }

    @Configuration @Import({Pedidos.class, Facturacion.class})
    public static class ConCiclo { }

    // ----- Rediseño: el ciclo se rompe con un evento. Pedidos publica; Facturacion escucha. -----
    public record PedidoConfirmado(String id) { }

    public static class PedidosSinCiclo {
        private final org.springframework.context.ApplicationEventPublisher eventos;
        public PedidosSinCiclo(org.springframework.context.ApplicationEventPublisher eventos) { this.eventos = eventos; }
        public void confirmar(String id) { eventos.publishEvent(new PedidoConfirmado(id)); }
    }

    public static class FacturacionSinCiclo {
        public final java.util.List<String> facturados = new java.util.ArrayList<>();
        @org.springframework.context.event.EventListener void alConfirmar(PedidoConfirmado e) { facturados.add(e.id()); }
    }

    @Configuration @Import({PedidosSinCiclo.class, FacturacionSinCiclo.class})
    public static class SinCiclo { }
}
