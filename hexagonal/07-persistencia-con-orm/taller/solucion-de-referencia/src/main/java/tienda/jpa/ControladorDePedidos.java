package tienda.jpa;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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

/** HTTP y JSON hacia los mismos puertos de entrada que usaban la consola y el servidor del JDK. */
@RestController
public class ControladorDePedidos {

    public record NuevoPedido(String cliente, List<Linea> lineas) {
    }

    private final CrearPedido crearPedido;
    private final ConfirmarPedido confirmarPedido;
    private final CancelarPedido cancelarPedido;
    private final ConsultarPedido consultarPedido;
    private final ListarPedidosDeCliente listarPedidos;

    public ControladorDePedidos(CrearPedido crearPedido, ConfirmarPedido confirmarPedido, CancelarPedido cancelarPedido,
                                ConsultarPedido consultarPedido, ListarPedidosDeCliente listarPedidos) {
        this.crearPedido = crearPedido;
        this.confirmarPedido = confirmarPedido;
        this.cancelarPedido = cancelarPedido;
        this.consultarPedido = consultarPedido;
        this.listarPedidos = listarPedidos;
    }

    @PostMapping("/pedidos")
    public ResponseEntity<VistaDePedido> crear(@RequestHeader("Idempotency-Key") String clave, @RequestBody NuevoPedido pedido) {
        Resultado resultado = crearPedido.crear(new Solicitud(clave, pedido.cliente(), pedido.lineas()));
        return ResponseEntity.status(resultado.nuevo() ? HttpStatus.CREATED : HttpStatus.OK).body(resultado.pedido());
    }

    @PostMapping("/pedidos/{id}/confirmacion")
    public VistaDePedido confirmar(@PathVariable String id) {
        return confirmarPedido.confirmar(id);
    }

    @PostMapping("/pedidos/{id}/cancelacion")
    public VistaDePedido cancelar(@PathVariable String id) {
        return cancelarPedido.cancelar(id);
    }

    @GetMapping("/pedidos/{id}")
    public VistaDePedido consultar(@PathVariable String id) {
        return consultarPedido.porId(id).orElseThrow(() -> new PedidoNoEncontrado(id));
    }

    @GetMapping("/pedidos")
    public List<VistaDePedido> deCliente(@RequestParam String cliente) {
        return listarPedidos.deCliente(cliente);
    }
}
