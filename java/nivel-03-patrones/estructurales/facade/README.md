# Facade

Familia: estructural. Frecuencia en Java empresarial: muy alta.

## El problema

Para lograr una tarea hay que coordinar varias clases de un subsistema, en un orden específico y con sus detalles. Cada cliente que necesita esa tarea repite la coreografía, conoce demasiadas piezas y se rompe cuando el subsistema cambia.

## Cómo reconocer que lo necesitas

- La misma secuencia de llamadas a varias clases aparece en más de un lugar.
- Para usar un módulo hay que entender cinco clases cuando al cliente le interesa un resultado.
- Quieres dar una entrada simple a un subsistema complejo o heredado, sin reescribirlo.
- Quieres definir la frontera pública de un módulo.

## Antes

```java
// En el controlador web, en el proceso nocturno y en el listener de eventos:
inventario.reservar(producto, cantidad);
String autorizacion = pagos.autorizar(cliente, total);
long guia = despachos.programar(cliente, producto);
facturacion.emitir(cliente, total, autorizacion);
notificaciones.enviar(cliente, "Tu pedido va en camino, guía " + guia);
```

## Después

<!-- file: ejemplo/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        ProcesoDeCompra compras = new ProcesoDeCompra(
                new Inventario(), new Pagos(), new Despachos(), new Notificaciones());

        System.out.println(compras.comprar("ana", "Teclado", 1, 120_000));
    }
}

/** La fachada: una operación con sentido de negocio en lugar de cuatro subsistemas. */
class ProcesoDeCompra {

    private final Inventario inventario;
    private final Pagos pagos;
    private final Despachos despachos;
    private final Notificaciones notificaciones;

    ProcesoDeCompra(Inventario inventario, Pagos pagos, Despachos despachos, Notificaciones notificaciones) {
        this.inventario = inventario;
        this.pagos = pagos;
        this.despachos = despachos;
        this.notificaciones = notificaciones;
    }

    String comprar(String cliente, String producto, int cantidad, long total) {
        inventario.reservar(producto, cantidad);
        String autorizacion = pagos.autorizar(cliente, total);
        long guia = despachos.programar(cliente, producto);
        notificaciones.enviar(cliente, "Tu pedido va en camino, guía " + guia);
        return "Compra " + autorizacion + " despachada con guía " + guia;
    }
}

class Inventario {
    void reservar(String producto, int cantidad) { System.out.println("Reservado: " + cantidad + " " + producto); }
}

class Pagos {
    String autorizar(String cliente, long total) { System.out.println("Pago autorizado: $" + total); return "AUT-481"; }
}

class Despachos {
    long programar(String cliente, String producto) { System.out.println("Despacho programado"); return 90_017L; }
}

class Notificaciones {
    void enviar(String cliente, String mensaje) { System.out.println("Aviso a " + cliente + ": " + mensaje); }
}
```

Los subsistemas siguen disponibles para quien necesite algo fino; la fachada cubre el caso común. No añade lógica nueva: coordina.

## Alternativas

- **Llamar directamente**, si la secuencia se usa una sola vez.
- **Mediator**, si las piezas necesitan comunicarse entre sí en ambos sentidos y no solo ser coordinadas desde afuera.
- **Adapter**, si el objetivo es encajar en un contrato existente y no simplificar.

## En Spring

- `JdbcTemplate`, `RestTemplate`/`RestClient`, `KafkaTemplate`: ocultan conexiones, recursos, conversión y manejo de errores detrás de pocas operaciones.
- Un **servicio de aplicación** (caso de uso) que coordina repositorios, dominio y notificaciones es una fachada sobre el modelo.
- La API pública de un módulo en un monolito modular es su fachada.
- SLF4J es una fachada sobre los distintos motores de logging.

## Ventajas, desventajas y trade-offs

| A favor | En contra |
|---|---|
| Los clientes dependen de una clase, no de cinco | Puede convertirse en un objeto dios |
| La coreografía está en un solo lugar | Si oculta demasiado, impide usos legítimos avanzados |
| Permite reorganizar el subsistema por dentro | Una capa que solo reenvía llamadas no aporta nada |
| Marca la frontera de un módulo | Las transacciones y los fallos parciales quedan a su cargo |

## Errores comunes

- Fachadas que acumulan reglas de negocio hasta ser el `ServicioPrincipal` de 3.000 líneas.
- Una única fachada para todo el sistema, en lugar de una por subsistema o por caso de uso.
- Fachadas que solo delegan uno a uno: `fachada.reservar()` llama a `inventario.reservar()` y nada más.
- Ignorar qué pasa si el paso tres falla después de que el uno y el dos tuvieron efecto.

## Cuándo no usarlo

Cuando el subsistema ya es simple; cuando cada cliente necesita una secuencia distinta; cuando la "fachada" sería un paso intermedio sin coordinación real.

## Taller

Un sistema de matrículas universitarias tiene: `RegistroAcademico` (verifica prerrequisitos y cupos), `Tesoreria` (genera el recibo y confirma el pago), `Horarios` (detecta cruces) y `Correo`.

1. Simula los cuatro subsistemas.
2. Crea la fachada `Matricula` con la operación `inscribir(estudiante, asignaturas)`.
3. Si `Horarios` detecta un cruce después de que `RegistroAcademico` reservó cupos, ¿qué debe pasar? Impleméntalo.
4. Tesorería necesita una operación que la fachada no ofrece (reimprimir recibo). ¿La agregas a la fachada o el cliente usa `Tesoreria` directamente? Justifica.
5. Tu fachada, ¿tiene reglas de negocio o solo coordina? Señala cada línea que sea una regla y decide dónde debería vivir.

Entrega: rama `taller/n3-facade`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).
