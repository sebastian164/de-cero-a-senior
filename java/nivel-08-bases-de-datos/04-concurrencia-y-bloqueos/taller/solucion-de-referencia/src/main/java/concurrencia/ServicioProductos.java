package concurrencia;

import java.math.BigDecimal;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicioProductos {

    private final ProductoRepositorio repositorio;

    public ServicioProductos(ProductoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public Producto ver(Long id) {
        return repositorio.findById(id).orElseThrow();
    }

    /**
     * Edición de un formulario: el cliente envía la versión que leyó (If-Match). Si no coincide,
     * otro usuario guardó antes y la edición se rechaza con 409.
     */
    @Transactional
    public Producto editarPrecio(Long id, long versionLeida, BigDecimal precio) {
        Producto p = repositorio.findById(id).orElseThrow();
        if (p.getVersion() != versionLeida) {
            throw new ObjectOptimisticLockingFailureException(Producto.class, id);
        }
        p.fijarPrecio(precio);
        return repositorio.saveAndFlush(p);
    }

    /** Optimista, sin reintento: con contención, falla. */
    @Transactional
    public void venderOptimista(Long id) {
        Producto p = repositorio.findById(id).orElseThrow();
        pausa();
        p.descontar(1);
    }

    /** Pesimista: SELECT … FOR UPDATE con tiempo límite. Los demás esperan. */
    @Transactional
    public void venderPesimista(Long id) {
        Producto p = repositorio.buscarParaActualizar(id).orElseThrow();
        pausa();
        p.descontar(1);
    }

    static void pausa() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
