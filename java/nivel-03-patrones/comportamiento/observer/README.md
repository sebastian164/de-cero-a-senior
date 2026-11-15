# Observer

Familia: comportamiento. Frecuencia en Java empresarial: muy alta (como eventos).

## El problema

Cuando ocurre algo en un objeto, otros deben reaccionar. Si el objeto llama directamente a cada interesado, queda acoplado a todos, y cada reacción nueva obliga a modificarlo. El que origina el hecho no debería saber quién se interesa ni para qué.

## Cómo reconocer que lo necesitas

- Un método de negocio termina con una lista creciente de "y además avisa a…".
- Un módulo central importa clases de módulos que deberían depender de él, no al revés.
- Necesitas agregar o quitar reacciones sin tocar a quien origina el hecho.
- Las reacciones no forman parte de la regla principal: son consecuencias.

## Antes

```java
void confirmar(Pedido pedido) {
    pedido.marcarConfirmado();
    correo.enviarConfirmacion(pedido);
    inventario.descontar(pedido);
    puntos.acumular(pedido);
    analitica.registrarVenta(pedido);   // agregado el mes pasado
    bodega.imprimirEtiqueta(pedido);    // agregado esta semana
}
```

## Después

<!-- file: ejemplo/Main.java -->
```java
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        ServicioPedidos pedidos = new ServicioPedidos();
        pedidos.suscribir(evento -> System.out.println("Correo: pedido " + evento.numero() + " confirmado"));
        pedidos.suscribir(evento -> System.out.println("Inventario: descontar pedido " + evento.numero()));
        pedidos.suscribir(new AcumuladorDePuntos());

        pedidos.confirmar("P-100", 180_000);
    }
}

/** El hecho, en pasado, con los datos que los interesados necesitan. */
record PedidoConfirmado(String numero, long valor) { }

interface OyentePedidos {
    void alConfirmar(PedidoConfirmado evento);
}

/** El sujeto: publica lo que ocurrió y no sabe quién escucha. */
class ServicioPedidos {

    private final List<OyentePedidos> oyentes = new ArrayList<>();

    void suscribir(OyentePedidos oyente) {
        oyentes.add(oyente);
    }

    void confirmar(String numero, long valor) {
        System.out.println("Pedido " + numero + " confirmado");
        PedidoConfirmado evento = new PedidoConfirmado(numero, valor);
        for (OyentePedidos oyente : oyentes) {
            oyente.alConfirmar(evento);
        }
    }
}

class AcumuladorDePuntos implements OyentePedidos {
    @Override
    public void alConfirmar(PedidoConfirmado evento) {
        System.out.println("Puntos: +" + evento.valor() / 1_000);
    }
}
```

`ServicioPedidos` ya no importa nada de correo, inventario ni puntos. Agregar una reacción es suscribir un oyente.

## Alternativas

- **Llamada directa**, si la reacción es parte esencial de la regla y debe fallar junto con ella.
- **Mediator**, si varios objetos se coordinan entre sí y no hay un emisor claro.
- **Cola o broker de mensajes** (Kafka, RabbitMQ), si los interesados están en otros procesos o la entrega debe sobrevivir a caídas.
- **Programación reactiva** (`Flux`, `Flow`), si se trata de flujos continuos con contrapresión.

## En Spring

```java
// publicar
eventos.publishEvent(new PedidoConfirmado(pedido.getNumero(), pedido.getTotal()));

// escuchar
@EventListener
void alConfirmar(PedidoConfirmado evento) { ... }
```

Tres detalles que separan a quien lo usa de quien lo entiende:

- **Por defecto es síncrono y en el mismo hilo.** Si un oyente lanza una excepción, llega hasta quien publicó y puede revertir la transacción.
- **`@TransactionalEventListener(phase = AFTER_COMMIT)`** ejecuta el oyente solo si la transacción se confirmó. Indispensable para correos y llamadas externas: no avises de algo que luego se revirtió.
- **`@Async`** en el oyente lo saca del hilo, pero entonces no hay garantía de entrega si el proceso cae. Para eso existe el patrón Outbox (Nivel 9).

## Ventajas, desventajas y trade-offs

| A favor | En contra |
|---|---|
| El emisor no conoce a los receptores | El flujo ya no se lee en un solo lugar |
| Reacciones nuevas sin tocar el emisor | El orden de ejecución no es evidente |
| Invierte dependencias entre módulos | Los errores de un oyente pueden afectar a los demás |
| Cada reacción se prueba sola | Fugas de memoria si los oyentes no se desuscriben |

## Errores comunes

- Usar eventos para lógica que debe ser atómica con la operación.
- Eventos con nombre de orden (`EnviarCorreo`) en lugar de hecho (`PedidoConfirmado`): el emisor vuelve a saber qué deben hacer los demás.
- Cascadas: un oyente publica otro evento, que dispara otro… y nadie puede seguir el flujo.
- Pasar la entidad mutable completa como evento, permitiendo que un oyente la modifique.
- Depender del orden de los oyentes.

## Cuándo no usarlo

Cuando hay un único interesado y siempre será así; cuando la reacción es parte de la regla; cuando la trazabilidad del flujo importa más que el desacople.

## Taller

Un sistema de biblioteca registra devoluciones de libros. Al devolver: se actualiza la disponibilidad, se avisa al siguiente usuario en la lista de espera, se calcula multa si hubo retraso y se actualizan las estadísticas de lectura.

1. Impleméntalo con llamadas directas y luego con Observer.
2. De las cuatro consecuencias, ¿cuál **no** debería ser un oyente sino parte de la operación? Justifica.
3. Haz que un oyente falle. ¿Qué pasa con los demás y con la devolución? Decide y programa la política.
4. Agrega la posibilidad de desuscribirse y muestra un caso donde olvidarlo causaría una fuga de memoria.
5. Escribe cómo quedaría con `ApplicationEventPublisher` y qué fase de transacción usarías para el aviso al usuario.

Entrega: rama `taller/n3-observer`.
