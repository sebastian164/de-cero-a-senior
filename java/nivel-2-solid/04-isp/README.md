# ISP — Principio de segregación de interfaces

Requisito: [LSP](../03-lsp/).

## 1. Explicación sencilla

Un restaurante tiene una carta de 40 páginas: desayunos, almuerzos, cocteles, menú infantil, eventos y domicilios. Tú solo quieres un café. Debes pasar todas las páginas para encontrarlo, y cuando el restaurante cambia el menú de eventos reimprime toda la carta, incluida la tuya.

Sería más sensato tener una carta de cafetería, otra de almuerzos y otra de eventos. Cada cliente recibe la que necesita y un cambio en una no afecta a quien usa otra.

## 2. Definición técnica

Ningún cliente debería verse obligado a depender de métodos que no usa.

Es el principio de responsabilidad única aplicado a los contratos: una interfaz debe ser cohesiva desde el punto de vista de **quien la consume**. Una interfaz grande que sirve a varios clientes distintos acopla a esos clientes entre sí: un cambio pedido por uno obliga a recompilar, volver a probar o volver a desplegar a los demás.

Las interfaces se diseñan por **rol** (qué necesita este cliente), no por **implementación** (todo lo que esta clase sabe hacer).

### Dos víctimas de una interfaz gorda

- **Quien la consume:** ve y depende de operaciones que no necesita.
- **Quien la implementa:** está obligado a dar cuerpo a métodos que no puede cumplir. Ahí aparecen los métodos vacíos y los `UnsupportedOperationException`, es decir, las violaciones de LSP.

## 3. Código que viola el principio

<!-- file: ejemplos/antes/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        Dispositivo oficina = new Multifuncional();
        Dispositivo recepcion = new ImpresoraBasica();

        oficina.imprimir("contrato.pdf");
        oficina.escanear("cedula");

        recepcion.imprimir("turno-17");
        try {
            recepcion.escanear("cedula");
        } catch (UnsupportedOperationException e) {
            System.out.println("FALLO: " + e.getMessage());
        }
    }
}

interface Dispositivo {
    void imprimir(String documento);
    String escanear(String nombre);
    void enviarFax(String documento, String numero);
    void grapar(String documento);
}

class Multifuncional implements Dispositivo {
    @Override public void imprimir(String documento) { System.out.println("Imprimiendo " + documento); }
    @Override public String escanear(String nombre) { System.out.println("Escaneando " + nombre); return nombre + ".png"; }
    @Override public void enviarFax(String documento, String numero) { System.out.println("Fax a " + numero); }
    @Override public void grapar(String documento) { System.out.println("Grapando " + documento); }
}

class ImpresoraBasica implements Dispositivo {
    @Override public void imprimir(String documento) { System.out.println("Imprimiendo " + documento); }

    @Override public String escanear(String nombre) {
        throw new UnsupportedOperationException("Esta impresora no escanea");
    }

    @Override public void enviarFax(String documento, String numero) {
        throw new UnsupportedOperationException("Esta impresora no envía fax");
    }

    @Override public void grapar(String documento) {
        // no hace nada
    }
}
```

## 4. Por qué es un problema

- **`ImpresoraBasica` está obligada a mentir.** Declara que cumple un contrato de cuatro operaciones y cumple una.
- **El error se descubre en ejecución.** Recepción recibe un `Dispositivo` y nada le advierte que no puede escanear.
- **Agregar una operación a la interfaz** (imprimir a doble cara) obliga a modificar todas las implementaciones, también las que no la soportan.
- **Un módulo que solo imprime depende de todo:** si cambia la firma de `enviarFax`, ese módulo se recompila y se vuelve a probar sin haber cambiado.
- **Probar a quien solo imprime** exige un doble con cuatro métodos, de los cuales tres son relleno.

## 5. Refactorización paso a paso

**Paso 1 — Listar los clientes y lo que usa cada uno.** Recepción: imprimir. Archivo: escanear. Gerencia: todo.

**Paso 2 — Una interfaz por rol.**

**Paso 3 — Cada clase implementa solo los roles que cumple.**

**Paso 4 — Cada cliente pide el rol más pequeño que le sirve.**

<!-- file: ejemplos/despues/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        Multifuncional multifuncional = new Multifuncional();
        ImpresoraBasica basica = new ImpresoraBasica();

        emitirTurno(basica, "turno-17");
        emitirTurno(multifuncional, "turno-18");
        digitalizar(multifuncional, "cedula");
        // digitalizar(basica, "cedula");  // no compila: no es un Escaner
    }

    static void emitirTurno(Impresora impresora, String turno) {
        impresora.imprimir(turno);
    }

    static void digitalizar(Escaner escaner, String nombre) {
        System.out.println("Guardado en " + escaner.escanear(nombre));
    }
}

interface Impresora {
    void imprimir(String documento);
}

interface Escaner {
    String escanear(String nombre);
}

interface Fax {
    void enviarFax(String documento, String numero);
}

class ImpresoraBasica implements Impresora {
    @Override public void imprimir(String documento) { System.out.println("Imprimiendo " + documento); }
}

class Multifuncional implements Impresora, Escaner, Fax {
    @Override public void imprimir(String documento) { System.out.println("Imprimiendo " + documento); }
    @Override public String escanear(String nombre) { System.out.println("Escaneando " + nombre); return nombre + ".png"; }
    @Override public void enviarFax(String documento, String numero) { System.out.println("Fax a " + numero); }
}
```

### Qué se ganó

- Ninguna clase implementa lo que no sabe hacer. Desaparecieron las excepciones y los métodos vacíos.
- Pedirle a la impresora básica que escanee es un error de compilación.
- Un cambio en `Fax` no toca a quien solo imprime.
- El doble de prueba para `emitirTurno` tiene un método.

### Qué se pagó

- Tres interfaces donde había una. Si un cliente necesita imprimir y escanear a la vez, debe pedir dos parámetros o definirse una interfaz que combine ambas (`interface Copiadora extends Impresora, Escaner`).
- `grapar` desapareció porque ningún cliente lo usaba. Eliminar es parte de segregar.

## 6. Ejemplo empresarial con Spring Boot

Fragmentos ilustrativos.

**Repositorios más estrechos que `JpaRepository`.** Al extender `JpaRepository` un repositorio expone decenas de métodos, incluidos `deleteAll()` y `deleteAllInBatch()`. Si una entidad nunca se elimina, ofrecer esa operación es una invitación al accidente. Spring Data permite declarar solo lo necesario extendiendo la interfaz marcadora `Repository`:

```java
public interface FacturaRepository extends Repository<Factura, Long> {
    Factura save(Factura factura);
    Optional<Factura> findById(Long id);
    List<Factura> findByClienteId(Long clienteId);
}
```

**Lectura y escritura por separado.**

```java
public interface ConsultaPedidos {
    Optional<PedidoDto> buscar(Long id);
    List<PedidoDto> listarPorCliente(Long clienteId);
}

public interface GestionPedidos {
    Long crear(CrearPedidoCommand comando);
    void cancelar(Long id);
}

@Service
class PedidoService implements ConsultaPedidos, GestionPedidos { ... }
```

El controlador de reportes inyecta `ConsultaPedidos` y no puede cancelar un pedido ni por error. Una sola clase implementa ambas: segregar interfaces no obliga a partir la implementación.

**La interfaz de servicio de 40 métodos.** `IPedidoService` con todo lo relacionado con pedidos es la forma más común de esta violación. La consume el controlador web, el proceso por lotes, el listener de eventos y el módulo de reportes; cada uno usa cinco métodos distintos.

**Clientes de APIs externas.** Un cliente HTTP que expone los 60 endpoints del proveedor a todos los módulos. Mejor: interfaces pequeñas por necesidad (`ConsultaSaldo`, `EnvioTransferencia`) implementadas por el mismo cliente.

**Ejemplos del framework:** `UserDetailsService` tiene un método. `Converter<S, T>`, uno. `ApplicationRunner`, uno. Son interfaces fáciles de implementar, de sustituir y de probar.

## 7. Taller práctico

<!-- file: taller/inicio/Main.java -->
```java
import java.util.List;

public class Main {

    public static void main(String[] args) {
        ServicioUsuarios servicio = new ServicioUsuariosEnMemoria();
        servicio.registrar("ana", "ana@correo.example");
        System.out.println(servicio.buscar("ana"));
        System.out.println(servicio.listarTodos());
    }
}

interface ServicioUsuarios {
    void registrar(String usuario, String correo);
    String buscar(String usuario);
    List<String> listarTodos();
    void cambiarContrasena(String usuario, String nueva);
    void bloquear(String usuario);
    void eliminar(String usuario);
    String exportarCsv();
    void enviarCorreoMasivo(String asunto, String cuerpo);
    int contarActivosUltimoMes();
}

class ServicioUsuariosEnMemoria implements ServicioUsuarios {
    private final java.util.Map<String, String> usuarios = new java.util.LinkedHashMap<>();

    @Override public void registrar(String usuario, String correo) { usuarios.put(usuario, correo); }
    @Override public String buscar(String usuario) { return usuarios.get(usuario); }
    @Override public List<String> listarTodos() { return List.copyOf(usuarios.keySet()); }
    @Override public void cambiarContrasena(String usuario, String nueva) { }
    @Override public void bloquear(String usuario) { }
    @Override public void eliminar(String usuario) { usuarios.remove(usuario); }
    @Override public String exportarCsv() { return String.join("\n", usuarios.keySet()); }
    @Override public void enviarCorreoMasivo(String asunto, String cuerpo) { }
    @Override public int contarActivosUltimoMes() { return usuarios.size(); }
}
```

Los consumidores de esta interfaz son:

- La pantalla de registro (app pública).
- El panel de administración (bloquear, eliminar, listar).
- El módulo de marketing (correo masivo, exportar).
- El tablero de métricas (contar activos).
- El módulo de perfil (buscar, cambiar contraseña).

1. Construye la matriz cliente × método: quién usa qué.
2. Propón las interfaces segregadas. Justifica cada frontera con la matriz.
3. Impleméntalo manteniendo una sola clase de implementación. Luego discute si esa clase también debería partirse (pista: SRP).
4. Escribe un cliente (por ejemplo, el tablero de métricas) y un doble de prueba para él. Compara el tamaño del doble antes y después.
5. ¿Alguna interfaz te quedó con un solo método? ¿Es un problema?

Entrega: rama `taller/04-isp`.

## 8. Preguntas de entrevista senior

1. ¿Qué diferencia hay entre SRP e ISP? ¿Puede una clase cumplir SRP e implementar una interfaz que viola ISP?
2. ¿Cómo decides el tamaño correcto de una interfaz? Da un criterio que no sea "pequeña".
3. ¿Cómo se manifiesta ISP en el diseño de una API REST o de un contrato gRPC?
4. Explica cómo una interfaz gorda termina causando violaciones de LSP.
5. Tienes una interfaz pública con 30 métodos y 50 clientes. ¿Cómo la segregas sin romper a nadie?
6. ¿Qué es una interfaz de rol y qué es una interfaz de cabecera (*header interface*)?
7. ¿Qué papel tiene ISP en el tiempo de compilación y en el despliegue independiente de módulos?

## 9. Cuándo aplicar ISP produce complejidad innecesaria

**Cuando hay un solo cliente o todos usan todo.** Si no hay clientes con necesidades distintas, no hay nada que segregar.

**Cuando se llega a una interfaz por método sin que nadie lo pida.** `Guardable`, `Buscable`, `Eliminable`, `Listable`: cuatro interfaces que siempre se usan juntas son una interfaz mal partida. El costo es tener que combinarlas en cada firma.

**Cuando la segregación sigue a la implementación y no a los clientes.** Partir por "qué métodos se parecen" en lugar de por "quién los usa" produce interfaces que no le sirven completas a nadie.

**En interfaces internas de un módulo pequeño.** Si el consumidor y el implementador cambian juntos y los mantiene la misma persona, el acoplamiento que ISP evita no cuesta nada.

**Cuando la explosión de tipos dificulta descubrir la API.** Diez interfaces diminutas pueden ser más difíciles de usar que una mediana bien documentada.

Prueba rápida: ¿existe hoy un cliente que use solo una parte, o una implementación que no pueda cumplir una parte? Si no, espera.

## Comprobación antes de avanzar

- Explicar por qué una interfaz grande acopla entre sí a sus clientes.
- Construir una matriz cliente × método y derivar interfaces de ella.
- Explicar la relación entre ISP y LSP.
- Dar un caso en el que segregar empeora el diseño.

Siguiente: [DIP — Principio de inversión de dependencias](../05-dip/).
