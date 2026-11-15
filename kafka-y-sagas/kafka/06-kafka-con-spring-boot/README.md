# 06 — Kafka con Spring Boot

El código de esta sesión usa Spring for Apache Kafka. Es ilustrativo: no se compiló al preparar el curso. Los nombres de algunas clases, sobre todo las de conversión a JSON, cambiaron entre las versiones 3 y 4 de la biblioteca; por eso los ejemplos convierten a mano y no dependen de ellas. Confirma el resto contra la documentación de tu versión.

## Idea

Hasta ahora conduciste con caja manual: tú abrías el bucle, llamabas a `poll`, confirmabas posiciones. Spring Boot es la caja automática. Te quita trabajo repetitivo y te deja concentrarte en la ruta. Pero quien nunca aprendió qué hace el embrague no entiende por qué el carro se apaga en una subida: todo lo de las sesiones 02 a 04 sigue ocurriendo debajo, aunque ya no lo escribas.

## Lo esencial

| Pieza | Qué hace | Lo que sigue siendo tu responsabilidad |
|---|---|---|
| `KafkaTemplate` | Publica. Envuelve un productor único y compartido | Clave, forma del evento, mirar el resultado |
| `@KafkaListener` | Ejecuta tu método por cada mensaje. Spring maneja el bucle, la confirmación y los hilos | Que el método sea idempotente y que **lance** cuando falla |
| Manejador de errores | Decide qué pasa cuando tu método lanza: reintentar, enviar a fallidos, detener | Elegir qué errores se reintentan y cuáles no |
| `concurrency` | Cuántos hilos consumen | No pasar del número de particiones |
| Modo de confirmación | Cuándo se confirma la posición: por mensaje, por lote o a mano | Entender qué se repite si el proceso cae |

La regla que más se incumple: **si tu método atrapa la excepción y no la relanza, Spring cree que todo salió bien y confirma.** El mensaje se perdió, y con una traza en el registro como único rastro.

### Reintentos: bloqueantes y no bloqueantes

| | Bloqueantes | No bloqueantes |
|---|---|---|
| Cómo | El hilo reintenta el mismo mensaje con espera | El mensaje se republica en temas de reintento y el consumo sigue |
| Orden por clave | Se conserva | **Se pierde:** los mensajes siguientes de esa clave adelantan al que falló |
| Partición | Se detiene mientras dura | Sigue |
| Para | Fallos breves; cuando el orden importa | Fallos largos; cuando el orden no importa |

## Antes

```java
@Service
public class InventarioListener {

    @Autowired private InventarioRepository repositorio;

    @KafkaListener(topics = "pedidos")
    public void escuchar(String mensaje) {
        try {
            ObjectMapper mapper = new ObjectMapper();                    // uno nuevo por mensaje
            Map<String, Object> datos = mapper.readValue(mensaje, Map.class);
            Integer productoId = (Integer) datos.get("producto_id");
            Integer cantidad = (Integer) datos.get("cant");
            Producto producto = repositorio.findById(productoId).get();
            producto.setExistencias(producto.getExistencias() - cantidad);   // no idempotente
            repositorio.save(producto);
        } catch (Exception e) {
            e.printStackTrace();                                         // y se sigue como si nada
        }
    }
}
```

## Análisis

| Problema | Consecuencia |
|---|---|
| `catch (Exception)` que no relanza | Todo mensaje que falla se pierde. La posición se confirma igual |
| Restar existencias sin comprobar repetidos | Cada reentrega descuenta otra vez |
| Sin `groupId` explícito | El grupo depende de una propiedad global; dos consumidores distintos pueden acabar compitiendo |
| `Map<String, Object>` y conversiones a mano | El contrato del evento no existe en el código; un cambio de tipo revienta en ejecución |
| Fallo pasajero y mensaje defectuoso se tratan igual | No hay reintento para lo que sí tendría arreglo ni destino para lo que no |
| Lógica de negocio dentro del adaptador de mensajería | No se puede probar sin Kafka |

## Después

```java
// El contrato, explícito
public record PedidoConfirmado(String id, String pedidoId, List<Linea> lineas) {
    public record Linea(String productoId, int cantidad) {}
}

// El adaptador de entrada: traduce y delega. Nada más.
@Component
class PedidosConfirmadosListener {

    private final ReservarInventario casoDeUso;
    private final LectorDeEventos lector;

    PedidosConfirmadosListener(ReservarInventario casoDeUso, LectorDeEventos lector) {
        this.casoDeUso = casoDeUso;
        this.lector = lector;
    }

    @KafkaListener(topics = "pedidos", groupId = "inventario", concurrency = "3")
    void alRecibir(ConsumerRecord<String, String> registro) {
        PedidoConfirmado evento = lector.leer(registro.value(), PedidoConfirmado.class);   // lanza EventoInvalido
        casoDeUso.ejecutar(evento);                                                        // si falla, lanza
    }
}

// El caso de uso: idempotente y transaccional. Se prueba sin Kafka.
@Service
class ReservarInventario {

    private final EventosProcesados procesados;
    private final Inventario inventario;

    ReservarInventario(EventosProcesados procesados, Inventario inventario) {
        this.procesados = procesados;
        this.inventario = inventario;
    }

    @Transactional
    public void ejecutar(PedidoConfirmado evento) {
        if (!procesados.registrarSiEsNuevo(evento.id())) {      // misma transacción que la reserva
            return;
        }
        inventario.reservar(evento.pedidoId(), evento.lineas());
    }
}

// La política de errores, en un solo lugar
@Configuration
class ErroresDeMensajeria {

    @Bean
    DefaultErrorHandler manejadorDeErrores(KafkaTemplate<String, String> kafka) {
        var aFallidos = new DeadLetterPublishingRecoverer(kafka);           // publica en el tema de fallidos
        var espera = new ExponentialBackOffWithMaxRetries(4);               // cuatro reintentos con espera creciente
        espera.setInitialInterval(500);
        espera.setMultiplier(2);

        var manejador = new DefaultErrorHandler(aFallidos, espera);
        manejador.addNotRetryableExceptions(EventoInvalido.class);          // defectuoso: directo a fallidos
        return manejador;
    }
}
```

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      acks: all
    consumer:
      auto-offset-reset: earliest
    listener:
      ack-mode: record        # confirma tras cada mensaje procesado
```

- El método lanza cuando falla. La decisión de qué hacer con el fallo vive en el manejador, no repartida en cada método.
- Un mensaje defectuoso va directo al tema de fallidos; un fallo pasajero se reintenta.
- La idempotencia está en el caso de uso, con su transacción.
- El adaptador solo traduce: es arquitectura hexagonal aplicada a la mensajería.

Publicar:

```java
@Component
class PublicadorDeEventos {

    private final KafkaTemplate<String, String> kafka;
    private final EscritorDeEventos escritor;

    PublicadorDeEventos(KafkaTemplate<String, String> kafka, EscritorDeEventos escritor) {
        this.kafka = kafka;
        this.escritor = escritor;
    }

    void publicar(String tema, String clave, Object evento) {
        kafka.send(tema, clave, escritor.escribir(evento))
             .whenComplete((resultado, error) -> {
                 if (error != null) {
                     // registrar y decidir. Ojo: si esto ocurre después de confirmar tu base de datos,
                     // ya tienes el problema de la doble escritura (sesión 07).
                 }
             });
    }
}
```

## Pruebas

| Qué | Cómo |
|---|---|
| El caso de uso | Prueba unitaria, sin Kafka |
| La lectura y escritura de eventos | Prueba unitaria con ejemplos del contrato |
| El adaptador con Kafka real | Prueba de integración con un contenedor (Testcontainers) |
| La política de errores | Prueba de integración: publicar un mensaje defectuoso y comprobar que aparece en fallidos y que el siguiente se procesa |

## Errores comunes

- Atrapar excepciones dentro del método del listener.
- `concurrency` mayor que las particiones.
- Reintentos no bloqueantes en un flujo donde el orden por clave importa.
- Dejar el `groupId` al azar o compartirlo entre aplicaciones.
- Lógica de negocio en el listener.
- Reintentos largos y bloqueantes que superan `max.poll.interval.ms` y provocan reequilibrios.
- No apagar de forma ordenada: en cada despliegue se reprocesan mensajes.
- Probar solo con dobles y descubrir en producción que la configuración no era la que se creía.

## Taller

**Objetivo:** construir el servicio de Inventario de la tienda con Spring Boot, con una política de errores defendible y pruebas de integración.

**Sobre tu servicio hexagonal:** si hiciste el [módulo de arquitectura hexagonal](../../../hexagonal/), construye Inventario como otro hexágono. `@KafkaListener` va en un adaptador de entrada, el caso de uso no lleva anotaciones de Kafka y la raíz de composición lo declara con `@Bean`. El paso 2 lo pide; aquí se concreta en puertos.

Usa los eventos que diseñaste en la sesión 05.

1. Crea el servicio. Consume `PedidoConfirmado` y reserva existencias en una base de datos. Si no hay suficientes, publica `InventarioInsuficiente`; si las hay, `InventarioReservado`.
2. Separa adaptador y caso de uso. El caso de uso debe tener pruebas unitarias que no arranquen Spring ni Kafka.
3. Hazlo idempotente. Demuéstralo publicando el mismo evento tres veces.
4. Configura el manejador de errores: reintentos con espera para fallos pasajeros, tema de fallidos para eventos inválidos. Decide y justifica qué excepciones van a cada grupo.
5. Provoca cada caso: un evento con formato inválido, la base de datos caída durante quince segundos y un error de programación (una excepción inesperada). Documenta qué hizo el sistema en cada uno.
6. Pon un `catch` que no relance, como en el "antes", y repite el paso 5. Cuenta los mensajes perdidos.
7. Sube `concurrency` a seis con un tema de tres particiones. ¿Qué pasa? Luego prueba con tres y mide el rendimiento frente a uno.
8. Cambia a reintentos no bloqueantes. Publica dos eventos seguidos de la misma clave, haciendo fallar el primero. ¿En qué orden se procesan? ¿En qué caso de tu tienda eso sería un error grave?
9. Escribe una prueba de integración con Testcontainers que publique un evento y compruebe la reserva, y otra que publique un evento inválido y compruebe que llega al tema de fallidos y que el siguiente se procesa.
10. Implementa el apagado ordenado y demuestra que un despliegue no produce reprocesamiento de más de un lote.
11. Expón el retraso del grupo como métrica y define a partir de qué valor alertarías.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Ningún mensaje se pierde ante los tres tipos de fallo | Paso 5, con conteos |
| Un evento repetido no descuenta dos veces | Paso 3 |
| El caso de uso se prueba sin infraestructura | Las pruebas unitarias corren en segundos |
| Las pruebas de integración usan un Kafka real | Paso 9 |

**Entrega:** rama `taller/kafka-06`, con el servicio en `taller/solucion/` y `ANALISIS.md`.

## En el trabajo real

En un equipo Java, casi todo el Kafka que vas a leer está escrito así. Lo que distingue a quien lo domina no es conocer las anotaciones, sino saber responder: si este método lanza, ¿qué pasa con el mensaje?, ¿y con los siguientes de esa partición?, ¿y si el proceso cae justo ahí?

## Preguntas senior

1. ¿Qué hace Spring por ti en un `@KafkaListener` y qué sigue siendo tu responsabilidad?
2. ¿Qué ocurre si el método del listener atrapa todas las excepciones?
3. Reintentos bloqueantes frente a no bloqueantes: efecto sobre el orden y sobre la partición.
4. ¿Dónde pones la idempotencia y por qué ahí?
5. ¿Cómo pruebas un consumidor de Kafka?
6. ¿Qué pasa con los mensajes en vuelo durante un despliegue?
