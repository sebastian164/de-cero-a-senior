package tienda;

import static tienda.Datos.solicitud;
import static tienda.Datos.unidades;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import tienda.adaptadores.salida.memoria.AlmacenEnMemoria;
import tienda.aplicacion.ConflictoDeConcurrencia;
import tienda.aplicacion.ServicioDePedidos;
import tienda.aplicacion.VistaDePedido;
import tienda.aplicacion.puertos.entrada.CrearPedido.Resultado;
import tienda.aplicacion.puertos.salida.Inventario;
import tienda.dominio.Existencias;
import tienda.dominio.ReglaDeNegocioViolada;

/** La última unidad, el control de versión, el reintento, la misma clave a la vez y muchos hilos. */
public final class PruebasDeConcurrencia {

    private PruebasDeConcurrencia() {
    }

    public static void ejecutar() {
        System.out.println("Concurrencia, sin control de versión (paso 1):");
        sinVersion();
        System.out.println("Concurrencia, con control de versión:");
        conVersion();
        elCasoDeUsoReintenta();
        elCasoDeUsoSeRinde();
        laMismaClaveALaVez();
        muchosHilos();
    }

    /** Queda un monitor; dos pedidos lo leen antes de que ninguno guarde. */
    private static void sinVersion() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        InventarioConInterferencia inventario = new InventarioConInterferencia(new InventarioIngenuo());
        inventario.guardar(Existencias.de("monitor", 1));
        ServicioDePedidos tienda = new ServicioDePedidos(almacen, Datos.catalogo(), inventario, almacen, new SinTransaccion(), Datos.RELOJ, almacen);

        inventario.interferir(1, () -> tienda.crear(solicitud("k-otro", "luis", "monitor:1")));
        tienda.crear(solicitud("k-1", "ana", "monitor:1"));
        int vendidas = tienda.deCliente("ana").size() + tienda.deCliente("luis").size();
        Comprobar.igual("se vendió dos veces el último monitor", 2, vendidas);
        Comprobar.igual("y las existencias dicen 0: vendidas + quedan = 2, no 1", 0, unidades(inventario, "monitor"));
    }

    private static void conVersion() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        InventarioConInterferencia inventario = new InventarioConInterferencia(almacen);
        almacen.guardar(Existencias.de("monitor", 1));
        ServicioDePedidos tienda = new ServicioDePedidos(almacen, Datos.catalogo(), inventario, almacen, almacen, Datos.RELOJ, almacen);

        inventario.interferir(1, () -> tienda.crear(solicitud("k-otro", "luis", "monitor:1")));
        Comprobar.lanza("el segundo choca, reintenta, ve que no queda y se rechaza", ReglaDeNegocioViolada.class,
                () -> tienda.crear(solicitud("k-1", "ana", "monitor:1")));
        Comprobar.igual("leyó dos veces: hubo reintento", 2, inventario.lecturas());
        Comprobar.igual("se vendió una sola unidad", List.of(0, 1), List.of(tienda.deCliente("ana").size(), tienda.deCliente("luis").size()));
        Comprobar.igual("y quedan 0", 0, unidades(almacen, "monitor"));
    }

    private static void elCasoDeUsoReintenta() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        InventarioConInterferencia inventario = new InventarioConInterferencia(almacen);
        Datos.sembrar(almacen, almacen);
        ServicioDePedidos tienda = new ServicioDePedidos(almacen, Datos.catalogo(), inventario, almacen, almacen, Datos.RELOJ, almacen);

        inventario.interferir(1, () -> tienda.crear(solicitud("k-otro", "luis", "teclado:3")));
        Resultado resultado = tienda.crear(solicitud("k-1", "ana", "teclado:2"));
        Comprobar.igual("el caso de uso termina bien al segundo intento", true, resultado.nuevo());
        Comprobar.igual("necesitó dos lecturas", 2, inventario.lecturas());
        Comprobar.igual("no se perdió ninguna venta: 10 - 3 - 2", 5, unidades(almacen, "teclado"));
    }

    private static void elCasoDeUsoSeRinde() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        InventarioConInterferencia inventario = new InventarioConInterferencia(almacen);
        Datos.sembrar(almacen, almacen);
        ServicioDePedidos tienda = new ServicioDePedidos(almacen, Datos.catalogo(), inventario, almacen, almacen, Datos.RELOJ, almacen);
        AtomicInteger otros = new AtomicInteger();

        inventario.interferir(100, () -> tienda.crear(solicitud("k-otro-" + otros.incrementAndGet(), "luis", "teclado:1")));
        Comprobar.lanza("tras " + ServicioDePedidos.INTENTOS + " intentos, el conflicto llega a quien llamó", ConflictoDeConcurrencia.class,
                () -> tienda.crear(solicitud("k-1", "ana", "teclado:2")));
        Comprobar.igual("intentó tres veces", ServicioDePedidos.INTENTOS, inventario.lecturas());
        inventario.interferir(0, null);
        Comprobar.igual("al rendirse no dejó existencias descontadas: solo las de los otros tres", 7, unidades(almacen, "teclado"));
        Comprobar.igual("ni pedido", 0, tienda.deCliente("ana").size());
        Comprobar.igual("y la misma clave se puede reintentar", true, tienda.crear(solicitud("k-1", "ana", "teclado:2")).nuevo());
        Comprobar.igual("quedan 5", 5, unidades(almacen, "teclado"));
    }

    private static void laMismaClaveALaVez() {
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        Datos.sembrar(almacen, almacen);
        ServicioDePedidos tienda = new ServicioDePedidos(almacen, Datos.catalogo(), almacen, almacen, almacen, Datos.RELOJ, almacen);
        Set<String> ids = ConcurrentHashMap.newKeySet();
        AtomicInteger nuevos = new AtomicInteger();
        AtomicInteger errores = new AtomicInteger();
        AtomicInteger rendidos = new AtomicInteger();
        try (ExecutorService grupo = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) {
                grupo.submit(() -> {
                    try {
                        Resultado resultado = tienda.crear(solicitud("k-repetida", "ana", "teclado:1"));
                        ids.add(resultado.pedido().id());
                        if (resultado.nuevo()) {
                            nuevos.incrementAndGet();
                        }
                    } catch (ConflictoDeConcurrencia conflicto) {
                        rendidos.incrementAndGet();
                    } catch (RuntimeException error) {
                        errores.incrementAndGet();
                    }
                });
            }
        }
        Comprobar.igual("ocho envíos simultáneos de la misma clave: un solo pedido", List.of(1, 1),
                List.of(tienda.deCliente("ana").size(), ids.size()));
        Comprobar.igual("uno es nuevo; los demás reciben el mismo pedido o se rinden; ningún otro error", List.of(1, 0),
                List.of(nuevos.get(), errores.get()));
        Comprobar.igual("se descontó una sola unidad", 9, unidades(almacen, "teclado"));
    }

    /** Devuelve cuántos pedidos se rindieron por conflicto. */
    static int muchosHilos() {
        int iniciales = 300;
        int hilos = 8;
        int porHilo = 40;
        AlmacenEnMemoria almacen = new AlmacenEnMemoria();
        almacen.guardar(Existencias.de("teclado", iniciales));
        ServicioDePedidos tienda = new ServicioDePedidos(almacen, Datos.catalogo(), almacen, almacen, almacen, Datos.RELOJ, almacen);
        AtomicInteger creados = new AtomicInteger();
        AtomicInteger sinExistencias = new AtomicInteger();
        AtomicInteger rendidos = new AtomicInteger();
        AtomicInteger otrosErrores = new AtomicInteger();
        AtomicInteger negativasVistas = new AtomicInteger();

        try (ExecutorService grupo = Executors.newFixedThreadPool(hilos)) {
            for (int h = 0; h < hilos; h++) {
                int hilo = h;
                grupo.submit(() -> {
                    for (int i = 0; i < porHilo; i++) {
                        int cantidad = 1 + (hilo + i) % 3;
                        try {
                            tienda.crear(solicitud("h" + hilo + "-" + i, "cliente-" + hilo, "teclado:" + cantidad));
                            creados.incrementAndGet();
                        } catch (ConflictoDeConcurrencia conflicto) {
                            rendidos.incrementAndGet();
                        } catch (ReglaDeNegocioViolada sinUnidades) {
                            sinExistencias.incrementAndGet();
                        } catch (RuntimeException error) {
                            otrosErrores.incrementAndGet();
                        }
                        if (unidades(almacen, "teclado") < 0) {
                            negativasVistas.incrementAndGet();
                        }
                    }
                });
            }
        }

        List<VistaDePedido> todos = new ArrayList<>();
        for (int h = 0; h < hilos; h++) {
            todos.addAll(tienda.deCliente("cliente-" + h));
        }
        int vendidas = todos.stream().mapToInt(pedido -> pedido.lineas().get(0).cantidad()).sum();
        int quedan = unidades(almacen, "teclado");
        System.out.println("  (" + creados + " creados, " + sinExistencias + " sin existencias, " + rendidos + " rendidos por conflicto)");
        Comprobar.igual("las existencias nunca se vieron negativas", 0, negativasVistas.get());
        Comprobar.igual("vendidas + quedan = iniciales", iniciales, vendidas + quedan);
        Comprobar.igual("cada pedido creado está guardado", creados.get(), todos.size());
        Comprobar.igual("cada intento terminó de una de tres formas, sin otros errores", List.of(hilos * porHilo, 0),
                List.of(creados.get() + sinExistencias.get() + rendidos.get(), otrosErrores.get()));
        return rendidos.get();
    }
}
