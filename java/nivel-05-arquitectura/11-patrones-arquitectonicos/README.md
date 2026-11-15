# 11 — Patrones arquitectónicos

Los patrones del Nivel 3 organizan clases. Estos organizan sistemas. Varios se desarrollan con más detalle en los niveles de bases de datos y sistemas distribuidos; aquí se trata de saber qué problema resuelve cada uno y cuándo no usarlo.

## Mapa

| Patrón | Problema que resuelve | Costo principal |
|---|---|---|
| CQRS | Lecturas y escrituras con necesidades distintas | Dos modelos que mantener |
| Arquitectura orientada a eventos | Acoplamiento entre componentes que reaccionan a hechos | Flujo difícil de seguir, consistencia eventual |
| Event Sourcing | Necesidad de historial completo y auditable | Complejidad alta, evolución de eventos |
| Saga | Operaciones de negocio que abarcan varios servicios | Compensaciones y estados intermedios |
| Outbox | Guardar en base y publicar un mensaje de forma confiable | Tabla y proceso adicionales |
| API Gateway | Punto de entrada único a muchos servicios | Punto central que puede crecer demasiado |
| Backend for Frontend | Clientes con necesidades distintas | Un backend por tipo de cliente |
| Strangler Fig | Reemplazar un sistema heredado sin apagarlo | Convivencia prolongada de dos sistemas |
| Capa anticorrupción | Proteger el modelo propio de uno ajeno | Código de traducción |
| Circuit Breaker | Fallos en cascada | Configuración y estados que entender |

## CQRS

*Command Query Responsibility Segregation*: separar el modelo que **cambia** el estado del modelo que lo **consulta**.

- **Comandos:** expresan una intención (`ConfirmarPedido`), pasan por el modelo de dominio y sus reglas, no devuelven datos.
- **Consultas:** devuelven datos con la forma que la pantalla necesita, sin pasar por el dominio.

```java
// Escritura: pasa por el agregado
class ConfirmarPedidoHandler {
    void manejar(ConfirmarPedido comando) {
        Pedido pedido = repositorio.cargar(comando.id());
        pedido.confirmar();
        repositorio.guardar(pedido);
    }
}

// Lectura: proyección directa, sin cargar el agregado
interface ConsultaPedidos {
    @Query("select new PedidoResumen(p.id, p.total, c.nombre) from ...")
    List<PedidoResumen> pendientesDe(Long clienteId);
}
```

Tiene niveles: desde separar clases de lectura y escritura sobre la misma base (barato y muy útil) hasta bases de datos distintas sincronizadas por eventos (caro, con consistencia eventual). **No uses el nivel alto si el bajo resuelve tu problema.** CQRS se aplica a las partes del sistema que lo necesitan, no a todo.

## Arquitectura orientada a eventos

Los componentes se comunican publicando hechos (`PedidoConfirmado`) en lugar de llamarse. El emisor no sabe quién reacciona.

- **Notificación:** el evento avisa; quien lo recibe consulta los detalles.
- **Transferencia de estado:** el evento lleva los datos; el receptor no necesita consultar.

Ventajas: desacople, extensión sin tocar el emisor, tolerancia a que un receptor esté caído. Costos: no se ve el flujo completo en ningún lugar, el orden y la duplicación de mensajes deben manejarse (consumidores idempotentes), y la consistencia es eventual.

## Event Sourcing

En lugar de guardar el estado actual, se guardan **todos los eventos** que llevaron a él. El estado se reconstruye reproduciéndolos.

```
CuentaAbierta(100) → Depositado(50) → Retirado(30)   ⇒   saldo = 120
```

Da auditoría completa, permite reconstruir el estado en cualquier momento del pasado y construir proyecciones nuevas sobre datos antiguos. A cambio: consultar exige proyecciones (va de la mano con CQRS), los eventos son contratos permanentes que hay que versionar, y el modelo mental es exigente. **Úsalo donde el historial es el negocio** (contabilidad, movimientos, trazabilidad legal), no por defecto.

## Saga

Una transacción de negocio que abarca varios servicios se parte en transacciones locales, cada una con una **acción de compensación** que la deshace.

```
Reservar inventario → Cobrar → Programar envío
        ↑ liberar ←── reembolsar ←── si el envío falla
```

| Estilo | Cómo funciona | Cuándo |
|---|---|---|
| Coreografía | Cada servicio reacciona a eventos del anterior | Flujos cortos |
| Orquestación | Un coordinador dirige los pasos | Flujos largos o con lógica condicional |

No hay aislamiento: otros ven estados intermedios. Las compensaciones deben ser idempotentes y algunas acciones no se pueden deshacer (un correo enviado).

## Outbox

Problema: guardar un pedido en la base **y** publicar un evento en el broker son dos operaciones que no comparten transacción. Si la segunda falla, el sistema queda inconsistente.

Solución: en la misma transacción que guarda el pedido se inserta el evento en una tabla `outbox`. Un proceso aparte lee esa tabla y publica. La entrega es "al menos una vez", por lo que los consumidores deben ser idempotentes.

## API Gateway y Backend for Frontend

- **API Gateway:** entrada única que enruta, autentica, limita la tasa y agrega respuestas. Riesgo: que acumule lógica de negocio.
- **BFF:** un backend por tipo de cliente (web, móvil), que entrega exactamente lo que esa interfaz necesita. Evita una API genérica que no le queda bien a nadie.

## Strangler Fig

Para reemplazar un sistema heredado: se pone una fachada delante, se construye la funcionalidad nueva al lado y se desvía el tráfico función por función. El sistema viejo se va vaciando hasta poder apagarse. Evita la reescritura total, que es la forma más común de fracaso en modernizaciones.

## Circuit Breaker

Cuando un servicio remoto falla repetidamente, el cortacircuitos se abre y deja de llamarlo durante un tiempo, respondiendo de inmediato con un error o una alternativa. Evita que los hilos se agoten esperando y que el fallo se propague. Estados: cerrado, abierto, semiabierto. En Java: Resilience4j.

## Cómo elegir

1. Parte del problema, no del patrón. Si no puedes nombrar el dolor que resuelve, no lo necesitas.
2. Cada patrón de esta lista añade complejidad operativa. Suma los costos antes de combinarlos.
3. La mayoría se puede adoptar por partes y solo donde hace falta.
4. Registra la decisión y sus razones en un ADR (se ve en el Nivel 12).

## Taller

Para cada escenario, indica qué patrón o combinación usarías, qué alternativa más simple consideraste y por qué la descartaste o la preferiste:

1. Un tablero de reportes hace consultas pesadas que bloquean las operaciones de escritura.
2. Al confirmar un pedido a veces se guarda pero el evento a bodega no llega.
3. Hay que reemplazar un sistema de facturación de 15 años sin detener la operación.
4. Una reserva de viaje implica vuelo, hotel y carro en tres servicios; si falla uno, se cancela todo.
5. La app móvil hace nueve llamadas para pintar la pantalla de inicio.
6. Una entidad financiera debe poder mostrar el saldo de una cuenta en cualquier fecha pasada y demostrar cómo se llegó a él.
7. Un proveedor externo se cae cada tanto y arrastra a toda la aplicación.

Luego implementa en Java puro una de estas dos opciones: una saga orquestada de tres pasos con compensación, o un agregado con Event Sourcing (cuenta bancaria reconstruida desde sus eventos).

Rama `taller/n5-11-patrones-arquitectonicos`.

## Preguntas senior

1. ¿CQRS exige Event Sourcing? ¿Y al revés?
2. Explica el problema de la doble escritura y cómo lo resuelve Outbox.
3. Coreografía u orquestación: criterios para elegir.
4. ¿Qué es la idempotencia y por qué es obligatoria en sistemas con eventos?
5. ¿Cuándo Event Sourcing es una mala idea?
6. ¿Qué es la consistencia eventual y cómo se la explicas a alguien del negocio?
7. ¿Por qué las reescrituras totales suelen fracasar y qué propone Strangler Fig?

Siguiente: [Taller integrador de arquitectura](../12-taller-integrador/).
