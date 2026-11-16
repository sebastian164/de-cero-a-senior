package jpa;

import jakarta.persistence.EntityManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

/** 50 pedidos, cada uno de un cliente distinto y con 2 líneas. */
final class Datos {

    static final LocalDate HOY = LocalDate.of(2026, 11, 15);

    static void cargar(EntityManager em, TransactionTemplate tx) {
        tx.executeWithoutResult(s -> {
            em.createQuery("delete from LineaPedido").executeUpdate();
            em.createQuery("delete from Pedido").executeUpdate();
            em.createQuery("delete from Cliente").executeUpdate();
            for (int i = 1; i <= 50; i++) {
                Cliente c = new Cliente("cliente-" + i, i % 2 == 0 ? "Bogotá" : "Medellín");
                em.persist(c);
                EstadoPedido estado = i <= 40 ? EstadoPedido.PENDIENTE : EstadoPedido.ENVIADO;
                Pedido p = new Pedido(c, estado, HOY.minusDays(i), i * 1_000L);
                p.agregarLinea("producto-a", 1);
                p.agregarLinea("producto-b", 2);
                em.persist(p);
            }
        });
        RegistroSql.limpiar();
    }

    private Datos() { }
}
