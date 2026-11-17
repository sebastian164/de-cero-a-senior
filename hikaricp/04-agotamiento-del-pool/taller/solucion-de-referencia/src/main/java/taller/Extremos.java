package taller;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Extremos {

    private final ConfirmarPedido confirmar;
    private final Pedidos pedidos;
    private final VistaDePedido vista;
    private final JdbcTemplate jdbc;

    public Extremos(ConfirmarPedido confirmar, Pedidos pedidos, VistaDePedido vista, JdbcTemplate jdbc) {
        this.confirmar = confirmar;
        this.pedidos = pedidos;
        this.vista = vista;
        this.jdbc = jdbc;
    }

    @PostMapping("/v1/pedidos/{id}/confirmar")
    public String confirmarV1(@PathVariable long id) {
        return confirmar.todoEnUnaTransaccion(id);
    }

    @PostMapping("/v2/pedidos/{id}/confirmar")
    public String confirmarV2(@PathVariable long id) {
        return confirmar.transaccionesCortas(id);
    }

    /** Paso 7: lee la entidad y tarda un segundo en "construir la respuesta", tocando una relación perezosa. */
    @GetMapping("/vista/pedidos/{id}")
    public Map<String, Object> vista(@PathVariable long id) {
        Pedido pedido = pedidos.findById(id).orElseThrow();
        dormir(1_000);                                           // construir la respuesta
        return Map.of("id", pedido.getId(), "lineas", pedido.getLineas().size());
    }

    /** Paso 7, corregido: funciona con open-in-view desactivado. */
    @GetMapping("/vista-corregida/pedidos/{id}")
    public Map<String, Object> vistaCorregida(@PathVariable long id) {
        VistaDePedido.Resumen resumen = vista.resumen(id);
        dormir(1_000);
        return Map.of("id", resumen.id(), "lineas", resumen.productos().size());
    }

    /** Paso 8: una consulta que con índice tarda milisegundos y sin él recorre la tabla entera. */
    @GetMapping("/historial/{cliente}")
    public Map<String, Object> historial(@PathVariable long cliente) {
        return jdbc.queryForMap("select count(*) as compras, coalesce(sum(importe), 0) as importe from historial where cliente_id = ?", cliente);
    }

    /** Paso 9: actualiza una fila y, con la transacción abierta, se queda pausa ms. */
    @Transactional
    @PostMapping("/bloqueo/{id}")
    public String bloqueo(@PathVariable long id, @RequestParam(defaultValue = "0") long pausa) {
        jdbc.update("update pedido set total = total + 1 where id = ?", id);
        dormir(pausa);
        return "actualizado";
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        }
    }
}
