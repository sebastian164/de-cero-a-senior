package tienda.adaptadores.entrada.consola;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.PedidoNoEncontrado;
import tienda.aplicacion.VistaDePedido;
import tienda.aplicacion.puertos.entrada.CancelarPedido;
import tienda.aplicacion.puertos.entrada.ConfirmarPedido;
import tienda.aplicacion.puertos.entrada.ConsultarPedido;
import tienda.aplicacion.puertos.entrada.CrearPedido;
import tienda.aplicacion.puertos.entrada.CrearPedido.Linea;
import tienda.aplicacion.puertos.entrada.CrearPedido.Resultado;
import tienda.aplicacion.puertos.entrada.CrearPedido.Solicitud;
import tienda.aplicacion.puertos.entrada.ListarPedidosDeCliente;
import tienda.dominio.ReglaDeNegocioViolada;

/** Traduce líneas de texto a los mismos puertos que usa HTTP. Habla en pesos. */
public final class Consola {

    private static final String USO = "uso: crear <clave> <cliente> <producto:cantidad>... | confirmar <pedido> | "
            + "cancelar <pedido> | ver <pedido> | pedidos <cliente>";

    private final CrearPedido crearPedido;
    private final ConfirmarPedido confirmarPedido;
    private final CancelarPedido cancelarPedido;
    private final ConsultarPedido consultarPedido;
    private final ListarPedidosDeCliente listarPedidos;
    private final PrintStream salida;

    public Consola(CrearPedido crearPedido, ConfirmarPedido confirmarPedido, CancelarPedido cancelarPedido,
                   ConsultarPedido consultarPedido, ListarPedidosDeCliente listarPedidos, PrintStream salida) {
        this.crearPedido = crearPedido;
        this.confirmarPedido = confirmarPedido;
        this.cancelarPedido = cancelarPedido;
        this.consultarPedido = consultarPedido;
        this.listarPedidos = listarPedidos;
        this.salida = salida;
    }

    public void atender(BufferedReader entrada) throws IOException {
        String linea;
        while ((linea = entrada.readLine()) != null) {
            if (!linea.isBlank()) {
                salida.println(ejecutar(linea.trim()));
            }
        }
    }

    private String ejecutar(String linea) {
        String[] partes = linea.split("\\s+");
        try {
            return switch (partes[0]) {
                case "crear" -> {
                    Solicitud solicitud = new Solicitud(partes[1], partes[2], Arrays.stream(partes, 3, partes.length).map(Consola::linea).toList());
                    Resultado resultado = crearPedido.crear(solicitud);
                    yield (resultado.nuevo() ? "creado " : "ya existía ") + describir(resultado.pedido());
                }
                case "confirmar" -> "confirmado " + describir(confirmarPedido.confirmar(partes[1]));
                case "cancelar" -> "cancelado " + describir(cancelarPedido.cancelar(partes[1]));
                case "ver" -> consultarPedido.porId(partes[1]).map(Consola::describir).orElse("no existe el pedido " + partes[1]);
                case "pedidos" -> listarPedidos.deCliente(partes[1]).stream().map(Consola::describir)
                        .collect(Collectors.joining(System.lineSeparator()));
                default -> "no entiendo: " + linea;
            };
        } catch (ReglaDeNegocioViolada | PedidoNoEncontrado | ConflictoDeConcurrencia rechazo) {
            return "rechazado: " + rechazo.getMessage();
        } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException malFormada) {
            return USO;
        }
    }

    private static Linea linea(String texto) {
        String[] productoCantidad = texto.split(":");
        return new Linea(productoCantidad[0], Integer.parseInt(productoCantidad[1]));
    }

    private static String describir(VistaDePedido pedido) {
        return pedido.id() + " de " + pedido.cliente() + ": " + pedido.estado() + " " + pesos(pedido.totalCentavos());
    }

    private static String pesos(long centavos) {
        String enteros = String.format(Locale.ROOT, "%,d", centavos / 100).replace(',', '.');
        return "$ " + enteros + String.format(Locale.ROOT, ",%02d", centavos % 100);
    }
}
