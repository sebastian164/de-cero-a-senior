package tienda.integrador.orquestador.dominio;

/** Una saga en curso: su estado, la orden que espera ver contestada y cuántas veces se ha reenviado. La versión la lleva el repositorio. */
public record Saga(String id, String correlacion, Estado estado, long version, long centavos, String producto, int cantidad, String ciudad,
                   String ordenPendiente, String destino, int intentos, String motivo, boolean revision) {

    public Saga conPaso(Transiciones.Paso paso, String motivoNuevo) {
        return new Saga(id, correlacion, paso.nuevoEstado(), version, centavos, producto, cantidad, ciudad, paso.orden(), paso.destino(), 0,
                motivoNuevo != null ? motivoNuevo : motivo, false);
    }

    public Saga conIntento(int intentosNuevos, boolean enRevision) {
        return new Saga(id, correlacion, estado, version, centavos, producto, cantidad, ciudad, ordenPendiente, destino, intentosNuevos, motivo, enRevision);
    }
}
