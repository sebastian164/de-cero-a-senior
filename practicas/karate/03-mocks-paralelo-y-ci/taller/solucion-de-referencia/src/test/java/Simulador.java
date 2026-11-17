import io.karatelabs.core.MockServer;

/** Arranca el simulador de Bitácora en el puerto indicado (por defecto 48090) y espera. */
public class Simulador {
    public static void main(String[] args) throws Exception {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : 48090;
        MockServer servidor = MockServer.feature("classpath:simulador/bitacora-simulada.feature").port(puerto).start();
        System.out.println("Simulador listo en " + servidor.getUrl());
        Thread.currentThread().join();
    }
}
