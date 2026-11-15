# 05 — Sagas con Kafka y Spring Boot

El código de esta sesión es ilustrativo: no se compiló al preparar el curso. Une lo que ya probaste por separado: los temas y las claves de Kafka, el consumidor idempotente, outbox y la máquina de estados del orquestador.

## Idea

Hasta ahora ensayaste la obra en una sala, con sillas en lugar de decorado. Los actores, el guion y las entradas y salidas ya están aprendidos. Ahora se monta en el teatro: hay telón, luces que fallan y público. La obra es la misma; lo que cambia es que cada cosa que antes era una variable en memoria ahora es una pieza de infraestructura con su propia manera de romperse.

## Lo esencial

### De la simulación a la infraestructura

| En las sesiones 02 a 04 | En un sistema real |
|---|---|
| El `Map` del almacén de sagas | Una tabla en la base de datos del orquestador |
| La cola del broker en memoria | Temas de Kafka |
| `enviar(...)` | Una fila en la tabla de salida, que un publicador lleva a Kafka |
| El `Set` de mensajes procesados | Una tabla, escrita en la misma transacción que el efecto |
| El contador de ciclos para el tiempo límite | Una tarea programada que busca sagas vencidas |
| La lista de casos para revisión | Una tabla o un tema de fallidos, con alerta |

### Temas y claves

| Tema | Quién escribe | Quién lee | Clave |
|---|---|---|---|
| `pagos.ordenes` | Orquestador | Pagos | Identificador de la saga |
| `inventario.ordenes` | Orquestador | Inventario | Identificador de la saga |
| `envios.ordenes` | Orquestador | Envíos | Identificador de la saga |
| `compra.respuestas` | Los tres participantes | Orquestador | Identificador de la saga |

La clave es el identificador de la saga en todos: así, las respuestas de una misma saga llegan al orquestador en orden y a la misma instancia mientras no haya reequilibrio. Entre temas distintos **no** hay orden: por eso siguen haciendo falta las lápidas.

### El ciclo de un paso

```
orquestador                              participante
───────────                              ────────────
transacción {                            transacción {
  leer respuesta                           ¿mensaje ya procesado? → salir
  ¿ya procesada? → salir                   hacer el trabajo local
  cambiar el estado de la saga             registrar el mensaje como procesado
  insertar la orden en "salida"            insertar la respuesta en "salida"
}                                        }
publicador → Kafka                       publicador → Kafka
```

Cada lado hace una sola transacción local. Kafka nunca participa en una transacción con la base: lo que cruza la frontera sale siempre por la tabla de salida.

## Antes

```java
@Component
class OrquestadorDeCompra {

    private final Map<String, String> estados = new ConcurrentHashMap<>();     // en memoria
    @Autowired private RestClient pagos;
    @Autowired private KafkaTemplate<String, String> kafka;
    @Autowired private PedidoRepository pedidos;

    @KafkaListener(topics = "pedidos")
    @Transactional
    public void alCrearPedido(String mensaje) {
        Pedido pedido = leer(mensaje);
        estados.put(pedido.getId(), "COBRANDO");
        try {
            pagos.post().uri("/cobros").body(pedido).retrieve().toBodilessEntity();   // llamada síncrona
            pedido.setEstado("PAGADO");
            pedidos.save(pedido);
            kafka.send("inventario.ordenes", mensaje);                                // dentro de la transacción
            estados.put(pedido.getId(), "RESERVANDO");
        } catch (Exception e) {
            estados.put(pedido.getId(), "FALLIDO");                                   // y nada más
        }
    }
}
```

## Análisis

| Problema | Consecuencia |
|---|---|
| Estado de la saga en un `Map` | Un reinicio lo borra. Con dos instancias, cada una tiene la mitad |
| Llamada HTTP síncrona dentro del listener | Si Pagos tarda, el consumidor supera `max.poll.interval.ms` y el grupo se reequilibra |
| El cobro no lleva clave de idempotencia | Si el mensaje se reentrega, se cobra dos veces |
| `kafka.send` dentro de `@Transactional` | Doble escritura: si la base no confirma, la orden ya salió; si el envío falla tarde, el pedido queda `PAGADO` sin orden |
| `catch (Exception)` que marca `FALLIDO` | No hay compensación, y el mensaje se confirma como procesado |
| Sin clave en el mensaje | Las órdenes de una misma saga pueden desordenarse |

## Después

```java
// La saga, como fila de una tabla
@Entity
class SagaDeCompra {
    @Id String id;
    @Enumerated(EnumType.STRING) Estado estado;
    @Version long version;                    // dos instancias no pisan la misma saga
    Instant venceEn;                          // tiempo límite del paso actual
    int intentos;
    // datos de la compra
}

// El adaptador de entrada: traduce y delega
@Component
class RespuestasListener {

    private final AvanzarSaga avanzar;
    private final LectorDeEventos lector;

    RespuestasListener(AvanzarSaga avanzar, LectorDeEventos lector) {
        this.avanzar = avanzar;
        this.lector = lector;
    }

    @KafkaListener(topics = "compra.respuestas", groupId = "orquestador-de-compra")
    void alRecibir(ConsumerRecord<String, String> registro) {
        avanzar.con(lector.leer(registro.value(), Respuesta.class));      // si falla, lanza
    }
}

// El caso de uso: una transacción local, y nada de Kafka aquí dentro
@Service
class AvanzarSaga {

    private final SagaRepository sagas;
    private final MensajesProcesados procesados;
    private final Salida salida;
    private final Transiciones transiciones;          // la tabla de transiciones de la sesión 02, como código puro

    AvanzarSaga(SagaRepository sagas, MensajesProcesados procesados, Salida salida, Transiciones transiciones) {
        this.sagas = sagas;
        this.procesados = procesados;
        this.salida = salida;
        this.transiciones = transiciones;
    }

    @Transactional
    public void con(Respuesta respuesta) {
        if (!procesados.registrarSiEsNuevo(respuesta.id())) {
            return;                                                       // repetida
        }
        SagaDeCompra saga = sagas.findById(respuesta.sagaId()).orElseThrow();
        Paso paso = transiciones.siguiente(saga.estado, respuesta.tipo());   // función pura, probada sin Spring
        if (paso.esIgnorar()) {
            return;                                                       // tardía o fuera de lugar
        }
        saga.estado = paso.nuevoEstado();
        saga.venceEn = paso.vencimiento();
        saga.intentos = 0;
        paso.orden().ifPresent(orden ->
                salida.agregar(orden.tema(), saga.id, orden.conCorrelacion(saga.id)));   // outbox, clave = saga
    }
}

// El reloj: sagas cuyo paso venció
@Component
class VigilanteDeSagas {

    private final SagaRepository sagas;
    private final ManejarVencimiento manejar;

    VigilanteDeSagas(SagaRepository sagas, ManejarVencimiento manejar) {
        this.sagas = sagas;
        this.manejar = manejar;
    }

    @Scheduled(fixedDelay = 5000)
    void revisar() {
        for (String id : sagas.idsVencidas(Instant.now())) {
            manejar.vencimientoDe(id);        // reintenta la orden o empieza a compensar; cada una en su transacción
        }
    }
}
```

- El estado vive en una tabla, con control de versión para que dos instancias no avancen la misma saga a la vez.
- El listener no llama a nadie: registra el cambio y deja la orden en la tabla de salida.
- La tabla de transiciones es una función pura. Las pruebas de la sesión 04 siguen valiendo sin cambios.
- Los tiempos límite los maneja un proceso aparte que consulta la tabla.

Un participante sigue el mismo patrón: comprobar si el mensaje es repetido, hacer su trabajo local, registrar el mensaje y dejar la respuesta en su propia tabla de salida, todo en una transacción.

## Observabilidad mínima

| Qué | Para qué |
|---|---|
| Identificador de saga en cada mensaje (cabecera) y en cada línea de registro | Seguir un caso por todos los servicios |
| Sagas por estado y sagas vencidas | Detectar atascos |
| Tiempo por paso y total | Saber cuál es lento |
| Casos en revisión humana | La métrica que nunca debería crecer |
| Retraso de cada grupo de consumidores | Saber si alguien dejó de leer |

## Errores comunes

- Estado de la saga en memoria o en una caché.
- Llamadas HTTP síncronas dentro de un listener.
- Publicar en Kafka dentro de la transacción de la base.
- Olvidar el control de versión con varias instancias del orquestador.
- Claves distintas en órdenes y respuestas de la misma saga.
- Reintentos no bloqueantes en las respuestas de la saga: desordenan.
- Probar solo el camino feliz con todo levantado.
- Sin vigilante: una respuesta que no llega deja la saga esperando sin fin.

## Taller

**Objetivo:** llevar tu orquestador a Kafka, PostgreSQL y Spring Boot, sin perder ninguna de las garantías que ya probaste en memoria.

Cuatro servicios: orquestador, Pagos, Inventario y Envíos. Pueden ser cuatro módulos de un mismo repositorio.

1. Define temas, claves y el formato de órdenes y respuestas, con el sobre de la sesión 05 de Kafka.
2. Lleva tu tabla de transiciones a un componente puro. Las pruebas de las sesiones 02 y 04 deben pasar sin cambios.
3. Implementa el orquestador: tabla de sagas, tabla de mensajes procesados, tabla de salida y publicador.
4. Implementa los tres participantes con el mismo patrón. Pagos puede simular la pasarela, pero debe usar clave de idempotencia.
5. Levanta todo con Compose y ejecuta los cuatro caminos. Demuestra el estado final consultando las bases.
6. **Caídas.** Mata, de uno en uno y en el peor momento que encuentres: el orquestador, un participante, el publicador y el broker. Tras cada reinicio, toda saga debe terminar.
7. **Dos instancias** del orquestador. Demuestra que una saga no avanza dos veces ni queda corrompida. Fuerza un conflicto de versión y muestra qué hace tu código.
8. **Tiempo límite.** Detén Inventario. Las sagas deben reintentar y luego compensar. Vuelve a levantar Inventario: ¿qué pasa con las órdenes que estaban esperando en el tema? Tu sistema debe quedar consistente.
9. **Mensaje defectuoso.** Publica a mano una respuesta ilegible en `compra.respuestas`. No debe bloquear las demás sagas de esa partición.
10. Escribe pruebas de integración con Testcontainers para un camino feliz y uno de compensación.
11. Expón las métricas de la tabla de observabilidad y define dos alertas.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Toda saga termina tras cualquier caída | Paso 6, con el listado de sagas no terminadas vacío |
| Ningún cobro doble | Conteo en la base de Pagos |
| Dos orquestadores conviven | Paso 7 |
| Las pruebas de la lógica no necesitan infraestructura | Paso 2 |

**Entrega:** rama `taller/sagas-05`, con el código en `taller/solucion/` y `ANALISIS.md`.

## En el trabajo real

Lo que acabas de construir es, en pequeño, lo que hace un motor de flujos duraderos. Haberlo escrito te deja en posición de decidir con criterio: si tu empresa tiene dos sagas, este código se mantiene bien; si tiene cuarenta, la tabla de sagas, el vigilante y los reintentos repetidos cuarenta veces son el argumento para adoptar un motor.

## Preguntas senior

1. ¿Por qué Kafka no puede formar parte de la transacción de la base de datos y cómo lo resuelves?
2. ¿Cómo evitas que dos instancias del orquestador avancen la misma saga?
3. ¿Qué clave usas en los temas de una saga y por qué?
4. ¿Cómo implementas tiempos límite sin bloquear hilos?
5. ¿Qué métricas te dicen que tus sagas están sanas?
6. ¿En qué momento cambiarías este diseño por un motor de flujos?
