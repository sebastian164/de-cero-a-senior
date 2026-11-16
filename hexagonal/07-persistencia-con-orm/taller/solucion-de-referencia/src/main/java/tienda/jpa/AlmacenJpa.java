package tienda.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.EventoDePedido;
import tienda.aplicacion.puertos.salida.Inventario;
import tienda.aplicacion.puertos.salida.PeticionesProcesadas;
import tienda.aplicacion.puertos.salida.PublicadorDeEventos;
import tienda.aplicacion.puertos.salida.RepositorioDePedidos;
import tienda.dominio.Existencias;
import tienda.dominio.Pedido;

/** Los puertos de salida de la tienda sobre JPA. Necesita una transacción abierta: la abre UnidadDeTrabajoJpa. */
@Repository
public class AlmacenJpa implements RepositorioDePedidos, Inventario, PeticionesProcesadas, PublicadorDeEventos {

    @PersistenceContext
    private EntityManager entidades;

    // ---- RepositorioDePedidos ----

    @Override
    public String siguienteId() {
        return "pedido-" + UUID.randomUUID();
    }

    @Override
    public void guardar(Pedido pedido) {
        PedidoJpa fila = entidades.find(PedidoJpa.class, pedido.id());
        if (fila == null) {
            entidades.persist(PedidoJpa.nuevo(pedido));          // INSERT del pedido y, en cascada, de sus líneas
            return;
        }
        fila.copiarDesde(pedido);                                // UPDATE pedido … WHERE id = ? AND version = ? al confirmar
    }

    @Override
    public Optional<Pedido> buscar(String pedidoId) {
        return entidades.createQuery("select p from PedidoJpa p left join fetch p.lineas where p.id = :id", PedidoJpa.class)
                .setParameter("id", pedidoId)
                .getResultList().stream().findFirst().map(PedidoJpa::aDominio);   // getResultStream fuera de una transacción
                                                                                      // devuelve un ResultSet ya cerrado
    }

    @Override
    public List<Pedido> deCliente(String cliente) {
        // Una sola consulta para los pedidos y sus líneas. Sin "join fetch" serían 1 + N (paso 8).
        return entidades.createQuery("select p from PedidoJpa p left join fetch p.lineas where p.cliente = :cliente "
                        + "order by p.creadoEn, p.id", PedidoJpa.class)
                .setParameter("cliente", cliente)
                .getResultList().stream().map(PedidoJpa::aDominio).toList();
    }

    // ---- Inventario ----

    @Override
    public Optional<Existencias> existenciasDe(String producto) {
        return Optional.ofNullable(entidades.find(ExistenciasJpa.class, producto)).map(ExistenciasJpa::aDominio);
    }

    @Override
    public void guardar(Existencias existencias) {
        ExistenciasJpa fila = entidades.find(ExistenciasJpa.class, existencias.producto());
        if (fila == null) {
            entidades.persist(ExistenciasJpa.nuevas(existencias));
            return;
        }
        fila.copiarDesde(existencias);       // la fila es la misma que se leyó en esta transacción: lleva su versión
    }

    // ---- PeticionesProcesadas ----

    @Override
    public Optional<String> pedidoDe(String clave) {
        return entidades.createNativeQuery("select pedido_id from peticion_procesada where clave = :clave", String.class)
                .setParameter("clave", clave)
                .getResultList().stream().findFirst().map(String.class::cast);
    }

    @Override
    public void registrar(String clave, String pedidoId) {
        entidades.flush();                                       // el pedido debe existir antes por la clave foránea
        // Si otra transacción inserta la misma clave, esta espera; si aquella confirma, aquí no se inserta nada.
        int insertadas = entidades.createNativeQuery("insert into peticion_procesada (clave, pedido_id) values (:clave, :pedido) "
                        + "on conflict do nothing")
                .setParameter("clave", clave)
                .setParameter("pedido", pedidoId)
                .executeUpdate();
        if (insertadas == 0) {
            throw new ConflictoDeConcurrencia("la petición " + clave);   // el reintento encontrará el pedido de la otra
        }
    }

    // ---- PublicadorDeEventos: bandeja de salida ----

    @Override
    public void publicar(EventoDePedido evento) {
        entidades.persist(EventoPendienteJpa.desde(evento));
    }
}
