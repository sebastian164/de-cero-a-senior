import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * Pasos 4, 5 y 6 del taller 01, sin modificar Pedidos.java: se compila junto a él y usa sus campos estáticos.
 * Termina con código 1 si alguna afirmación del ANALISIS.md deja de ser cierta.
 */
public class Reproducir {

    static int fallos = 0;

    public static void main(String[] args) throws Exception {
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/pedidos", Pedidos::crearPedido);
        servidor.start();
        int puerto = servidor.getAddress().getPort();
        try {
            pasoCuatro(puerto);
            pasoCinco(puerto);
        } finally {
            servidor.stop(0);
        }
        pasoSeis();
        System.out.println(fallos == 0 ? "TODO COMPROBADO" : fallos + " FALLOS");
        System.exit(fallos == 0 ? 0 : 1);
    }

    /** El tope de 5.000.000 de pesos no se alcanza con las existencias que trae el código. */
    static void pasoCuatro(int puerto) throws Exception {
        System.out.println("== Paso 4: intentar probar el tope ==");
        long maximo = 0;
        for (var producto : Pedidos.PRECIOS.entrySet()) {
            maximo += producto.getValue() * Pedidos.EXISTENCIAS.get(producto.getKey());
        }
        System.out.println("pedido más caro posible con las existencias iniciales: " + maximo + " centavos (tope 500000000)");
        afirmar("el tope es inalcanzable sin cambiar EXISTENCIAS", maximo <= 500_000_000L);
        String respuesta = Pedidos.enviar(puerto, "cliente=c-tope&linea=teclado:10&linea=raton:3&linea=monitor:2");
        System.out.println("todo el inventario en un pedido -> " + respuesta);
        afirmar("se acepta: el tope nunca salta", respuesta.startsWith("201"));
        // Con más existencias (EXISTENCIAS es estático y accesible) la regla sí salta, y deja el mismo defecto del paso 5.
        reponer();
        Pedidos.EXISTENCIAS.put("monitor", 6);
        String rechazo = Pedidos.enviar(puerto, "cliente=c-tope&linea=monitor:6");
        System.out.println("monitor:6 con 6 en existencias -> " + rechazo + "  existencias de monitor: " + Pedidos.EXISTENCIAS.get("monitor"));
        afirmar("el tope rechaza con 422 supera el tope", rechazo.startsWith("422") && rechazo.contains("supera el tope"));
        afirmar("pero las existencias quedaron descontadas", Pedidos.EXISTENCIAS.get("monitor") == 0);
        reponer();
    }

    /** Un pedido con una línea válida y una inválida descuenta las existencias de la primera. */
    static void pasoCinco(int puerto) throws Exception {
        System.out.println("== Paso 5: las existencias que desaparecen ==");
        reponer();
        System.out.println("antes:   " + new HashMap<>(Pedidos.EXISTENCIAS));
        String r1 = Pedidos.enviar(puerto, "cliente=cliente-9&linea=teclado:2&linea=monitor:5");
        System.out.println("teclado:2 + monitor:5 -> " + r1);
        System.out.println("después: " + new HashMap<>(Pedidos.EXISTENCIAS));
        afirmar("rechazado por existencias", r1.equals("422 {\"error\":\"sin existencias\"}"));
        afirmar("pero se descontaron 2 teclados de un pedido que no existe", Pedidos.EXISTENCIAS.get("teclado") == 8);

        String r2 = Pedidos.enviar(puerto, "cliente=&linea=raton:1");
        System.out.println("sin cliente, raton:1 -> " + r2 + "  existencias de raton: " + Pedidos.EXISTENCIAS.get("raton"));
        afirmar("falta el cliente también descuenta", Pedidos.EXISTENCIAS.get("raton") == 2);

        String r3 = Pedidos.enviar(puerto, "cliente=c-1&linea=raton:1&linea=lampara:1");
        System.out.println("raton:1 + lampara:1 -> " + r3 + "  existencias de raton: " + Pedidos.EXISTENCIAS.get("raton"));
        afirmar("un producto desconocido en la segunda línea también", Pedidos.EXISTENCIAS.get("raton") == 1);

        boolean sinRespuesta;
        try {
            Pedidos.enviar(puerto, "cliente=c-1&linea=raton:dos");
            sinRespuesta = false;
        } catch (java.io.IOException cortada) {
            sinRespuesta = true;
        }
        System.out.println("raton:dos -> " + (sinRespuesta ? "la conexión se cierra sin respuesta (NumberFormatException sin capturar)" : "respondió"));
        afirmar("una cantidad que no es número deja al cliente sin respuesta", sinRespuesta);
        reponer();
    }

    /**
     * El servidor del código de inicio no tiene ejecutor: atiende una petición tras otra en un solo hilo, así que la carrera
     * no se ve. Con un ejecutor (como cualquier servidor real) dos peticiones por el último monitor pueden venderlo dos veces.
     */
    static void pasoSeis() throws Exception {
        System.out.println("== Paso 6: dos peticiones por el último monitor ==");
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/pedidos", Pedidos::crearPedido);
        servidor.setExecutor(Executors.newFixedThreadPool(8));
        servidor.start();
        int puerto = servidor.getAddress().getPort();
        int intentos = 1000;
        // Un solo cliente HTTP para todas las carreras: Pedidos.enviar crea uno por petición y no lo cierra,
        // y 2000 carreras agotaban los hilos del sistema.
        java.net.http.HttpClient cliente = java.net.http.HttpClient.newHttpClient();
        int vendidoDosVeces = 0;
        List<Integer> negativos = new ArrayList<>();
        java.io.PrintStream pantalla = System.out;
        System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));   // calla los "correos"
        try {
            for (int i = 0; i < intentos; i++) {
                reponer();
                Pedidos.EXISTENCIAS.put("monitor", 1);
                CountDownLatch salida = new CountDownLatch(1);
                List<CompletableFuture<String>> dos = new ArrayList<>();
                for (int p = 0; p < 2; p++) {
                    dos.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            salida.await();
                            return enviar(cliente, puerto, "cliente=c&linea=monitor:1");
                        } catch (Exception e) {
                            return "error " + e;
                        }
                    }));
                }
                salida.countDown();
                long aceptados = dos.stream().map(CompletableFuture::join).filter(r -> r.startsWith("201")).count();
                if (aceptados == 2) {
                    vendidoDosVeces++;
                    negativos.add(Pedidos.EXISTENCIAS.get("monitor"));
                }
            }
        } finally {
            System.setOut(pantalla);
            servidor.stop(0);
            ((java.util.concurrent.ExecutorService) servidor.getExecutor()).shutdownNow();
        }
        System.out.println("de " + intentos + " carreras, el último monitor se vendió dos veces en " + vendidoDosVeces
                + (negativos.isEmpty() ? "" : "; existencias finales en esos casos: " + negativos.stream().distinct().toList()));
        // Es una carrera: puede no salir en una máquina concreta. Se informa, no se exige.
        reponer();
    }

    static String enviar(java.net.http.HttpClient cliente, int puerto, String cuerpo) throws Exception {
        var peticion = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + puerto + "/pedidos"))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(cuerpo)).build();
        var respuesta = cliente.send(peticion, java.net.http.HttpResponse.BodyHandlers.ofString());
        return respuesta.statusCode() + " " + respuesta.body();
    }

    static void reponer() {
        Pedidos.EXISTENCIAS.putAll(Map.of("teclado", 10, "raton", 3, "monitor", 2));
    }

    static void afirmar(String que, boolean cierto) {
        System.out.println((cierto ? "  ok    " : "  FALLA ") + que);
        if (!cierto) {
            fallos++;
        }
    }
}
