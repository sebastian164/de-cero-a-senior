package concurrencia;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Las operaciones de {@link ServicioConReintento}, cada una en su propia transacción. Cuenta intentos y sesiones. */
@Service
public class OperacionesTransaccionales {

    private final AtomicInteger intentos = new AtomicInteger();
    private final List<Integer> sesiones = new CopyOnWriteArrayList<>();
    private final ProductoRepositorio repositorio;

    @PersistenceContext
    private EntityManager em;

    public OperacionesTransaccionales(ProductoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public AtomicInteger intentos() {
        return intentos;
    }

    public List<Integer> sesiones() {
        return sesiones;
    }

    private void anotarIntento() {
        intentos.incrementAndGet();
        // El EntityManager que la transacción actual tiene ligado: uno nuevo por transacción (el "em" inyectado es un proxy compartido).
        var holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(em.getEntityManagerFactory());
        sesiones.add(System.identityHashCode(holder.getEntityManager()));
    }

    @Transactional
    public void fijarPrecio(Long id, BigDecimal precio) {
        anotarIntento();
        Producto p = repositorio.findById(id).orElseThrow();
        ServicioProductos.pausa();
        p.fijarPrecio(precio);
    }

    @Transactional
    public void vender(Long id) {
        anotarIntento();
        Producto p = repositorio.findById(id).orElseThrow();
        ServicioProductos.pausa();
        p.descontar(1);
    }
}
