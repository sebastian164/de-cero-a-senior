/** Regla 2 en un único lugar: qué transición es válida desde cada estado. */
public enum EstadoPedido {
    CREADO, PAGADO, ENVIADO;

    EstadoPedido siguiente(EstadoPedido destino) {
        boolean valida = (this == CREADO && destino == PAGADO)
                || (this == PAGADO && destino == ENVIADO);
        if (!valida) {
            throw new IllegalStateException("No se puede pasar de " + this + " a " + destino);
        }
        return destino;
    }
}
