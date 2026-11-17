package consumidores;

/** Puerto de entrada del servicio: lo que el negocio sabe hacer con un pago. No conoce Kafka. Cuando retorna, el adaptador confirma. */
public interface ProcesarPago {

    record Pago(int particion, long posicion, String valor) {
    }

    void procesar(Pago pago) throws java.io.IOException;
}
