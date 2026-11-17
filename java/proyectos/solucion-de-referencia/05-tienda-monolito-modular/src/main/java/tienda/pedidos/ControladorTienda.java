package tienda.pedidos;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tienda.catalogo.Catalogo;
import tienda.inventario.Inventario;

import java.math.BigDecimal;

/** Borde HTTP mínimo de la tienda (lo usa también el despliegue del proyecto 6). */
@RestController
public class ControladorTienda {
    public record NuevoProducto(String sku, String nombre, BigDecimal precio, int stock) {}
    public record NuevoPedido(String cliente, String sku, int cantidad) {}

    private final Pedidos pedidos; private final Catalogo catalogo; private final Inventario inventario;

    public ControladorTienda(Pedidos pedidos, Catalogo catalogo, Inventario inventario) {
        this.pedidos = pedidos; this.catalogo = catalogo; this.inventario = inventario;
    }

    @PostMapping("/productos") @ResponseStatus(HttpStatus.CREATED)
    public void producto(@RequestBody NuevoProducto p) { catalogo.crear(p.sku(), p.nombre(), p.precio()); inventario.ingresar(p.sku(), p.stock()); }

    @PostMapping("/pedidos")
    public Pedidos.Pedido pedir(@RequestBody NuevoPedido p) { return pedidos.confirmar(p.cliente(), p.sku(), p.cantidad()); }

    @GetMapping("/pedidos/{id}")
    public Pedidos.Pedido ver(@PathVariable long id) { return pedidos.buscar(id); }
}
