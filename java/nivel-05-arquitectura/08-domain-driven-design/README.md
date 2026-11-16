# 08 — Domain-Driven Design

## Idea sencilla

Si construyes un sistema para una clínica veterinaria y no sabes qué distingue una consulta de un control, ni qué significa "paciente" para ellos, vas a programar muy bien algo que no les sirve. DDD propone que el software se construya sobre un entendimiento profundo del negocio, compartido entre quienes lo conocen y quienes lo programan, y que ese entendimiento sea visible en el código.

## Explicación técnica

Domain-Driven Design (Eric Evans, 2003) es un enfoque para sistemas con **dominios complejos**. Tiene dos mitades. La estratégica es la más valiosa y la más ignorada.

### Diseño estratégico

**Lenguaje ubicuo.** Un vocabulario preciso, acordado con los expertos del negocio, que se usa igual en las conversaciones, en los documentos y en el código. Si el negocio dice "radicar una solicitud", el método se llama `radicar`, no `save`.

**Contexto delimitado (*bounded context*).** Una frontera dentro de la cual un modelo y su lenguaje son consistentes. La misma palabra significa cosas distintas en contextos distintos, y está bien:

| Palabra | En Ventas | En Bodega | En Facturación |
|---|---|---|---|
| "Producto" | Algo con precio y descripción comercial | Algo con peso, ubicación y existencias | Un concepto con tarifa de impuesto |
| "Cliente" | Un prospecto con historial de compras | Una dirección de entrega | Un responsable fiscal |

Intentar un único modelo "Producto" para toda la empresa produce una clase gigante que no le sirve bien a nadie.

**Subdominios.** Partes del negocio, clasificadas por su valor:

| Tipo | Qué es | Estrategia |
|---|---|---|
| Núcleo (*core*) | Lo que diferencia a la empresa | El mejor diseño y las mejores personas |
| De soporte | Necesario, no diferenciador | Solución sencilla |
| Genérico | Igual en cualquier empresa (autenticación, facturación electrónica) | Comprar o usar algo existente |

**Mapa de contextos.** Cómo se relacionan los contextos. La relación más útil de conocer es la **capa anticorrupción**: un traductor que impide que el modelo de un sistema ajeno (o heredado) contamine el propio. Es un Adapter a escala de contexto.

### Diseño táctico

Los bloques para implementar el modelo dentro de un contexto:

| Bloque | Qué es | Ejemplo |
|---|---|---|
| **Entidad** | Objeto con identidad que persiste aunque cambien sus atributos | `Pedido`, `Cuenta` |
| **Objeto de valor** | Definido por sus atributos, inmutable, sin identidad | `Dinero`, `Direccion`, `Periodo` |
| **Agregado** | Grupo de objetos tratado como una unidad de consistencia, con una entidad raíz | `Pedido` con sus `LineaPedido` |
| **Repositorio** | Colección de agregados; oculta la persistencia | `RepositorioPedidos` |
| **Servicio de dominio** | Operación del negocio que no pertenece a una entidad | `ServicioDeTransferencias` |
| **Evento de dominio** | Algo relevante que ocurrió | `PedidoConfirmado` |
| **Fábrica** | Creación compleja de agregados | `Pedido.nuevo(…)` |

### Las reglas de los agregados

Son lo más difícil y lo más útil de la parte táctica:

1. **Solo la raíz se referencia desde afuera.** Nadie modifica una `LineaPedido` directamente: se le pide al `Pedido`.
2. **Un agregado protege sus invariantes** dentro de sus límites, siempre.
3. **Una transacción modifica un solo agregado.** La consistencia entre agregados es eventual, mediante eventos.
4. **Los agregados se referencian entre sí por identificador,** no por objeto: `Pedido` tiene un `ClienteId`, no un `Cliente`.
5. **Agregados pequeños.** Un agregado grande genera contención y carga datos innecesarios.

```java
public class Pedido {                                   // raíz del agregado

    private final PedidoId id;
    private final ClienteId clienteId;                  // referencia por identificador
    private final List<LineaPedido> lineas = new ArrayList<>();
    private EstadoPedido estado = EstadoPedido.BORRADOR;

    public void agregarLinea(ProductoId producto, int cantidad, Dinero precioUnitario) {
        exigirQueEsteEnBorrador();
        if (lineas.size() >= MAXIMO_LINEAS) throw new LimiteDeLineasExcedido(id);
        lineas.add(new LineaPedido(producto, cantidad, precioUnitario));
    }

    public PedidoConfirmado confirmar() {               // el nombre es lenguaje del negocio
        exigirQueEsteEnBorrador();
        if (lineas.isEmpty()) throw new PedidoVacio(id);
        estado = EstadoPedido.CONFIRMADO;
        return new PedidoConfirmado(id, total());       // evento de dominio
    }

    public Dinero total() {
        return lineas.stream().map(LineaPedido::subtotal).reduce(Dinero.CERO, Dinero::mas);
    }

    public List<LineaPedido> lineas() { return List.copyOf(lineas); }
}
```

No hay setters. Cada método es una operación que el negocio reconoce.

## Errores comunes

- **DDD táctico sin estratégico:** entidades, repositorios y agregados sobre un modelo que nadie validó con el negocio. Es la forma más común de "hacer DDD" y la que menos valor da.
- **Agregados enormes:** `Cliente` con todos sus pedidos, facturas y direcciones cargados juntos.
- **Un repositorio por tabla** en lugar de uno por agregado.
- **Lenguaje técnico disfrazado:** `PedidoManager.procesarPedido()` no es lenguaje ubicuo.
- **Un solo modelo para toda la empresa**, ignorando los contextos.
- **Aplicarlo a un CRUD:** si no hay reglas, no hay dominio que modelar.
- **Contextos delimitados convertidos automáticamente en microservicios:** un contexto es una frontera de modelo, no necesariamente de despliegue.

## Trade-offs

- A favor: el software refleja el negocio y evoluciona con él; fronteras claras para dividir equipos y sistemas; menos traducción mental entre negocio y código.
- En contra: exige acceso real y continuo a expertos del negocio; curva de aprendizaje alta; inversión inicial considerable; el mapeo a bases relacionales tiene fricción.

## Cuándo no

Dominios simples, aplicaciones de registro de datos, proyectos sin acceso a quien conoce el negocio, subdominios genéricos. Evans mismo lo dice: es para la complejidad del núcleo del negocio.

## Taller

Elige un negocio que conozcas bien (uno en el que hayas trabajado).

1. **Lenguaje:** escribe un glosario de quince términos. Marca los que significan cosas distintas para áreas distintas.
2. **Contextos:** identifica de tres a cinco contextos delimitados y dibuja su mapa. Señala dónde pondrías una capa anticorrupción.
3. **Subdominios:** clasifica cada uno como núcleo, soporte o genérico y di cómo invertirías el esfuerzo.
4. **Agregado:** modela en Java puro un agregado del núcleo, con sus invariantes, objetos de valor y al menos un evento de dominio. Sin setters.
5. **Límites:** ¿qué dejaste fuera del agregado y cómo se mantiene consistente con él?
6. **Autocrítica:** ¿qué parte de ese negocio no necesita DDD?

Rama `taller/n5-08-ddd`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Qué es un contexto delimitado y por qué un modelo único para toda la empresa fracasa?
2. ¿Cómo decides los límites de un agregado? Da los criterios.
3. ¿Por qué una transacción debería modificar un solo agregado y qué haces cuando el negocio pide consistencia entre dos?
4. ¿Qué es una capa anticorrupción y cuándo la usarías?
5. ¿Relación entre contextos delimitados y microservicios?
6. ¿Cómo persistes un agregado con JPA sin que JPA deforme el modelo?
7. ¿Cuándo le dirías a un equipo que no use DDD?

Siguiente: [09 — Modularidad](../09-modularidad/).
