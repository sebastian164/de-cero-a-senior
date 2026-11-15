/** Una conexión nueva por cada petición. */
public class Main {

    static int conexionesAbiertas = 0;

    static final class Conexion {
        Conexion() {
            dormir(20);                      // red, cifrado, autenticación, sesión en la base
            conexionesAbiertas++;
        }

        String consultar(String sql) {
            dormir(2);
            return "resultado de: " + sql;
        }

        void cerrar() {
            // se destruye de verdad: la siguiente petición paga otra vez los 20 ms
        }
    }

    public static void main(String[] args) {
        long inicio = System.nanoTime();
        for (int peticion = 1; peticion <= 50; peticion++) {
            Conexion conexion = new Conexion();
            conexion.consultar("select * from pedidos where id = " + peticion);
            conexion.cerrar();
        }
        long milisegundos = (System.nanoTime() - inicio) / 1_000_000;

        System.out.println("Peticiones atendidas: 50");
        System.out.println("Conexiones abiertas:  " + conexionesAbiertas);
        System.out.println("Tiempo total:         unos " + milisegundos + " ms");
    }

    static void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
        }
    }
}
