package concurrencia;

/**
 * Dos hilos toman dos recursos en orden opuesto. Con el argumento "corregido",
 * ambos los toman en el mismo orden (por un criterio global) y terminan.
 */
public class Interbloqueo {
    static final Object CUENTA_A = new Object();
    static final Object CUENTA_B = new Object();

    static void transferir(String nombre, Object primero, Object segundo) {
        synchronized (primero) {
            dormir(100);                                   // agranda la ventana para que se crucen
            synchronized (segundo) {
                System.out.println(nombre + " terminó");
            }
        }
    }

    static void dormir(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public static void main(String[] args) throws InterruptedException {
        boolean corregido = args.length > 0 && args[0].equals("corregido");
        Thread h1 = new Thread(() -> transferir("transferencia-1", CUENTA_A, CUENTA_B), "transferencia-1");
        Thread h2 = corregido
                ? new Thread(() -> transferir("transferencia-2", CUENTA_A, CUENTA_B), "transferencia-2")   // mismo orden
                : new Thread(() -> transferir("transferencia-2", CUENTA_B, CUENTA_A), "transferencia-2");  // orden opuesto
        System.out.println("listo pid=" + ProcessHandle.current().pid());
        h1.start();
        h2.start();
        h1.join();
        h2.join();
        System.out.println("fin");
    }
}
