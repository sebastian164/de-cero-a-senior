package tienda;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tienda.dominio.Dinero;
import tienda.dominio.EstadoDePedido;
import tienda.dominio.Existencias;
import tienda.dominio.LineaDePedido;
import tienda.dominio.Pedido;
import tienda.dominio.ReglaDeNegocioViolada;
import tienda.dominio.Reserva;

/** Cada regla de los pedidos, con sus límites exactos, sin nada alrededor. */
public final class PruebasDelDominio {

    private static final Instant AHORA = Instant.parse("2026-11-15T10:00:00Z");
    private static final Dinero TECLADO = new Dinero(15_000_000);
    private static final Dinero MONITOR = new Dinero(90_000_000);

    private PruebasDelDominio() {
    }

    public static void ejecutar() {
        System.out.println("Dominio, dinero y líneas:");
        Comprobar.lanza("un valor no puede ser negativo", ReglaDeNegocioViolada.class, () -> new Dinero(-1));
        Comprobar.igual("el subtotal es precio por cantidad", new Dinero(30_000_000), new LineaDePedido("teclado", 2, TECLADO).subtotal());
        Comprobar.lanza("cantidad cero no es una línea", ReglaDeNegocioViolada.class, () -> new LineaDePedido("teclado", 0, TECLADO));
        Comprobar.lanza("cantidad negativa no es una línea", ReglaDeNegocioViolada.class, () -> new LineaDePedido("teclado", -1, TECLADO));
        Comprobar.igual("la cantidad mínima es 1", 1, new LineaDePedido("teclado", 1, TECLADO).cantidad());
        Comprobar.lanza("una línea sin producto no existe", ReglaDeNegocioViolada.class, () -> new LineaDePedido(" ", 1, TECLADO));
        Comprobar.lanza("una línea sin precio no existe", ReglaDeNegocioViolada.class, () -> new LineaDePedido("teclado", 1, null));

        System.out.println("Dominio, pedido:");
        Comprobar.lanza("sin cliente no hay pedido", ReglaDeNegocioViolada.class, () -> Pedido.crear("p", "", List.of(linea("teclado", 1)), AHORA));
        Comprobar.lanza("con cliente nulo tampoco", ReglaDeNegocioViolada.class, () -> Pedido.crear("p", null, List.of(linea("teclado", 1)), AHORA));
        Comprobar.lanza("sin líneas no hay pedido", ReglaDeNegocioViolada.class, () -> Pedido.crear("p", "ana", List.of(), AHORA));
        Comprobar.lanza("con líneas nulas tampoco", ReglaDeNegocioViolada.class, () -> Pedido.crear("p", "ana", null, AHORA));
        Comprobar.igual("un pedido justo en el tope se acepta", Pedido.TOPE,
                Pedido.crear("p", "ana", List.of(new LineaDePedido("x", 1, Pedido.TOPE)), AHORA).total());
        Comprobar.lanza("un centavo por encima del tope se rechaza", ReglaDeNegocioViolada.class,
                () -> Pedido.crear("p", "ana", List.of(new LineaDePedido("x", 1, Pedido.TOPE), new LineaDePedido("y", 1, new Dinero(1))), AHORA));
        Pedido pedido = Pedido.crear("p-1", "ana", List.of(linea("teclado", 2), linea("monitor", 1), linea("teclado", 1)), AHORA);
        Comprobar.igual("el total suma las líneas", new Dinero(135_000_000), pedido.total());
        Comprobar.igual("las líneas repetidas se suman por producto", Map.of("teclado", 3, "monitor", 1), pedido.unidadesPorProducto());
        Comprobar.igual("un pedido nuevo nace pendiente", EstadoDePedido.PENDIENTE, pedido.estado());
        Comprobar.lanza("las líneas no se modifican desde fuera", UnsupportedOperationException.class,
                () -> pedido.lineas().add(linea("raton", 1)));
        List<LineaDePedido> original = new ArrayList<>(List.of(linea("teclado", 1)));
        Pedido copia = Pedido.crear("p-2", "ana", original, AHORA);
        original.add(linea("monitor", 100));
        Comprobar.igual("ni cambiando la lista con la que se creó", 1, copia.lineas().size());

        System.out.println("Dominio, estados:");
        Pedido aConfirmar = nuevo();
        aConfirmar.confirmar();
        Comprobar.igual("un pedido pendiente se confirma", EstadoDePedido.CONFIRMADO, aConfirmar.estado());
        Comprobar.lanza("uno confirmado no se confirma otra vez", ReglaDeNegocioViolada.class, aConfirmar::confirmar);
        aConfirmar.cancelar();
        Comprobar.igual("uno confirmado se puede cancelar", EstadoDePedido.CANCELADO, aConfirmar.estado());
        Comprobar.lanza("uno cancelado no se confirma", ReglaDeNegocioViolada.class, aConfirmar::confirmar);
        Comprobar.lanza("uno cancelado no se cancela otra vez", ReglaDeNegocioViolada.class, aConfirmar::cancelar);
        Pedido aCancelar = nuevo();
        aCancelar.cancelar();
        Comprobar.igual("un pedido pendiente se cancela", EstadoDePedido.CANCELADO, aCancelar.estado());

        System.out.println("Dominio, existencias:");
        Comprobar.lanza("no hay existencias negativas", ReglaDeNegocioViolada.class, () -> Existencias.de("raton", -1));
        Existencias ratones = Existencias.de("raton", 3);
        ratones.reservar(3);
        Comprobar.igual("se puede vender la última unidad", 0, ratones.unidades());
        Comprobar.lanza("una de más se rechaza", ReglaDeNegocioViolada.class, () -> ratones.reservar(1));
        Comprobar.igual("y no descuenta nada", 0, ratones.unidades());
        Comprobar.lanza("no se reservan cero unidades", ReglaDeNegocioViolada.class, () -> ratones.reservar(0));
        ratones.devolver(2);
        Comprobar.igual("devolver suma", 2, ratones.unidades());

        System.out.println("Dominio, reserva del pedido entero (el defecto del taller 01):");
        Map<String, Existencias> inventario = inventario(10, 2);
        Pedido conSegundaLineaImposible = Pedido.crear("p-3", "ana", List.of(linea("teclado", 2), linea("monitor", 5)), AHORA);
        Comprobar.lanza("si una línea no alcanza, se rechaza el pedido", ReglaDeNegocioViolada.class,
                () -> Reserva.reservar(conSegundaLineaImposible, inventario));
        Comprobar.igual("y las existencias de la primera línea siguen intactas", 10, inventario.get("teclado").unidades());
        Comprobar.lanza("un producto sin existencias registradas también se rechaza", ReglaDeNegocioViolada.class,
                () -> Reserva.reservar(Pedido.crear("p-4", "ana", List.of(linea("raton", 1)), AHORA), inventario));
        Comprobar.lanza("las líneas repetidas cuentan juntas contra las existencias", ReglaDeNegocioViolada.class,
                () -> Reserva.reservar(Pedido.crear("p-5", "ana", List.of(linea("monitor", 1), linea("monitor", 2)), AHORA), inventario));
        Comprobar.igual("sin tocar nada", 2, inventario.get("monitor").unidades());
        Pedido valido = Pedido.crear("p-6", "ana", List.of(linea("teclado", 2), linea("monitor", 2)), AHORA);
        Reserva.reservar(valido, inventario);
        Comprobar.igual("un pedido que alcanza descuenta cada producto", List.of(8, 0),
                List.of(inventario.get("teclado").unidades(), inventario.get("monitor").unidades()));
        Reserva.liberar(valido, inventario);
        Comprobar.igual("y liberarlo las devuelve", List.of(10, 2),
                List.of(inventario.get("teclado").unidades(), inventario.get("monitor").unidades()));
    }

    private static LineaDePedido linea(String producto, int cantidad) {
        return new LineaDePedido(producto, cantidad, producto.equals("monitor") ? MONITOR : TECLADO);
    }

    private static Pedido nuevo() {
        return Pedido.crear("p", "ana", List.of(linea("teclado", 1)), AHORA);
    }

    private static Map<String, Existencias> inventario(int teclados, int monitores) {
        Map<String, Existencias> inventario = new LinkedHashMap<>();
        inventario.put("teclado", Existencias.de("teclado", teclados));
        inventario.put("monitor", Existencias.de("monitor", monitores));
        return inventario;
    }
}
