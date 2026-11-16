# 05 — Clean Architecture

## Idea sencilla

Una ciudad amurallada tiene en el centro lo más valioso: el tesoro y los archivos. Alrededor, los talleres. En la muralla, las puertas por donde entra y sale todo. Los mercaderes de afuera conocen las puertas; el tesoro no conoce a los mercaderes. Se puede cambiar una puerta o un mercader sin tocar el centro.

## Explicación técnica

Clean Architecture (Robert C. Martin, 2012) organiza el sistema en círculos concéntricos y establece una única regla:

> **Regla de dependencia:** las dependencias del código fuente apuntan solo hacia adentro. Nada de un círculo interno conoce algo de uno externo.

```
┌────────────────────────────────────────────────┐
│ Frameworks y drivers: web, base de datos, UI   │
│  ┌──────────────────────────────────────────┐  │
│  │ Adaptadores de interfaz: controladores,  │  │
│  │ presentadores, repositorios concretos    │  │
│  │  ┌────────────────────────────────────┐  │  │
│  │  │ Casos de uso: reglas de aplicación │  │  │
│  │  │  ┌──────────────────────────────┐  │  │  │
│  │  │  │ Entidades: reglas de negocio │  │  │  │
│  │  │  └──────────────────────────────┘  │  │  │
│  │  └────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────┘  │
└────────────────────────────────────────────────┘
```

| Círculo | Contiene | Cambia cuando |
|---|---|---|
| Entidades | Reglas de negocio que existirían aunque no hubiera software | Cambia el negocio en lo esencial |
| Casos de uso | Lo que la aplicación hace: orquestan entidades para cumplir un objetivo del usuario | Cambia un proceso |
| Adaptadores | Traducción entre el formato de los casos de uso y el del exterior | Cambia un canal o una tecnología |
| Frameworks y drivers | Spring, la base de datos, el servidor web | Se actualiza o reemplaza una herramienta |

### Cómo cruza los límites el flujo de control

Un caso de uso necesita guardar. No puede conocer el repositorio concreto (está afuera). Declara una **interfaz de salida** en su círculo; el adaptador de afuera la implementa. Es la inversión de la lección anterior.

Los datos que cruzan un límite son estructuras simples definidas por el círculo interior. Nunca una entidad JPA ni un objeto de petición HTTP.

### Un caso de uso

```java
// Círculo de casos de uso: no importa Spring, JPA ni HTTP
public class ConfirmarPedido {

    private final RepositorioPedidos pedidos;        // puerto de salida
    private final PasarelaPagos pagos;               // puerto de salida

    public ConfirmarPedido(RepositorioPedidos pedidos, PasarelaPagos pagos) {
        this.pedidos = pedidos;
        this.pagos = pagos;
    }

    public PedidoConfirmado ejecutar(ConfirmarPedidoComando comando) {
        Pedido pedido = pedidos.buscar(comando.pedidoId())
                .orElseThrow(() -> new PedidoNoEncontrado(comando.pedidoId()));
        ResultadoPago pago = pagos.cobrar(pedido.total(), comando.medioDePago());
        pedido.confirmar(pago);                      // la regla vive en la entidad
        pedidos.guardar(pedido);
        return new PedidoConfirmado(pedido.id(), pedido.total());
    }
}
```

Una clase por caso de uso, con un método público. El nombre de la clase dice lo que hace el sistema.

### Estructura de paquetes

```
com.tienda.pedidos
├── dominio            Pedido, Dinero, EstadoPedido, PedidoNoEncontrado
├── aplicacion
│   ├── ConfirmarPedido, CancelarPedido, ConsultarPedido
│   └── puertos        RepositorioPedidos, PasarelaPagos
├── adaptadores
│   ├── web            PedidoController, PedidoRequest, PedidoResponse
│   ├── persistencia   PedidoJpaEntity, RepositorioPedidosJpa, PedidoMapper
│   └── pagos          PasarelaStripe
└── configuracion      PedidosConfig   (ensambla casos de uso como beans)
```

**Arquitectura que grita:** al abrir el proyecto se debe ver de qué trata (pedidos, confirmar, cancelar), no qué framework usa.

## Errores comunes

- **Ceremonia sin sustancia:** cuatro círculos, tres mapeadores y dos interfaces para un CRUD. El resultado es mucho código que solo traslada datos.
- **Casos de uso anémicos:** si todo caso de uso es `repositorio.guardar(x)`, no había reglas que proteger.
- **Entidades de dominio con anotaciones JPA** "para ahorrar el mapeo": es un compromiso válido si es consciente, pero ya no es la regla de dependencia estricta.
- **Interfaz de entrada por cada caso de uso** sin necesidad: la de salida es la que invierte dependencias; la de entrada suele ser prescindible.
- **Carpetas con los nombres correctos y dependencias incorrectas:** tener un paquete `dominio` que importa Spring no es Clean Architecture.

## Trade-offs

| A favor | En contra |
|---|---|
| El negocio es independiente de frameworks, UI y base de datos | Mucho código de traducción entre círculos |
| Casos de uso probables en milisegundos | Curva de aprendizaje para el equipo |
| La estructura comunica el propósito del sistema | Excesiva para dominios simples |
| Las decisiones técnicas se posponen o se revierten | Más archivos para seguir un flujo |

## Cuándo usarla y cuándo no

Conviene cuando el dominio tiene reglas reales y vida larga, cuando hay varios canales o integraciones, o cuando el costo de un error de negocio es alto. No conviene en CRUDs, prototipos, servicios pequeños de traducción o cuando el equipo no la entiende y la aplicará como plantilla.

## Taller

Implementa el módulo "reservas de salas de reunión" con Clean Architecture.

Reglas: una sala no puede tener dos reservas que se solapen; las reservas duran entre 30 minutos y 4 horas; solo quien reservó puede cancelar, y hasta una hora antes del inicio.

1. Define entidades y objetos de valor sin ninguna dependencia de framework.
2. Implementa los casos de uso `ReservarSala`, `CancelarReserva` y `ConsultarDisponibilidad`.
3. Implementa dos adaptadores de persistencia: uno en memoria y otro JPA (el segundo puede quedar descrito si no montas la base).
4. Prueba todas las reglas sin Spring.
5. Mide el costo: ¿cuántas clases creaste?, ¿cuántas son traducción pura? Opina si este módulo lo justificaba.

Rama `taller/n5-05-clean-architecture`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. Enuncia la regla de dependencia y explica cómo un caso de uso llama a la base de datos sin violarla.
2. ¿Qué diferencia hay entre una regla de negocio de entidad y una regla de aplicación de caso de uso?
3. ¿Dónde colocas las transacciones sin que el caso de uso dependa de Spring?
4. ¿Mapearías entre entidad de dominio y entidad JPA siempre? Da criterios.
5. ¿Cómo explicas a un gerente el costo adicional de esta arquitectura y qué recibe a cambio?
6. ¿Qué significa "arquitectura que grita"?

Siguiente: [06 — Arquitectura hexagonal](../06-arquitectura-hexagonal/).
