package transacciones;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicioInventario {

    private final InventarioRepositorio repositorio;

    public ServicioInventario(InventarioRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    /** El error: leer, calcular en la aplicación y escribir. Con concurrencia se pierden ventas. */
    @Transactional
    public void venderIngenuo(Long id) {
        Inventario inv = repositorio.findById(id).orElseThrow();
        pausa();
        inv.descontar(1);
    }

    /** La corrección: la base hace el cálculo en una sola sentencia. */
    @Transactional
    public boolean venderAtomico(Long id) {
        return repositorio.descontarAtomico(id, 1) == 1;
    }

    private static void pausa() {
        try {
            Thread.sleep(3);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
