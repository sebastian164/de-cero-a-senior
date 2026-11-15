# 06 — Arquitectura hexagonal (puertos y adaptadores)

## Idea sencilla

Un portátil tiene puertos USB. No sabe si conectarás un ratón, una memoria o una impresora: define la forma del puerto y cualquier dispositivo con el conector adecuado funciona. Cuando un dispositivo usa otro conector, se pone un adaptador entre los dos.

La aplicación es el portátil: define sus puertos. El mundo exterior se conecta mediante adaptadores.

## Explicación técnica

Propuesta por Alistair Cockburn en 2005. Su objetivo original: que la aplicación pueda ser manejada igual por usuarios, programas, pruebas automáticas o scripts, y desarrollada y probada aislada de sus dispositivos y bases de datos.

```
        Adaptadores primarios                 Adaptadores secundarios
        (conducen la aplicación)              (son conducidos por ella)

   REST ──────┐                                    ┌────── JPA / PostgreSQL
              ▼                                    ▼
   Mensajes ─► [Puerto de entrada] APLICACIÓN [Puerto de salida] ◄─ Cliente HTTP
              ▲          Y DOMINIO                 ▲
   Pruebas ───┘                                    └────── Kafka
```

| Concepto | Qué es | Ejemplo |
|---|---|---|
| **Puerto de entrada** (primario) | Lo que la aplicación ofrece | `interface ConfirmarPedido` |
| **Puerto de salida** (secundario) | Lo que la aplicación necesita del exterior | `interface RepositorioPedidos` |
| **Adaptador primario** | Traduce una tecnología de entrada a llamadas a un puerto | Controlador REST, listener de mensajes, CLI |
| **Adaptador secundario** | Implementa un puerto de salida con una tecnología | Repositorio JPA, cliente HTTP, publicador Kafka |

El hexágono no significa seis de nada: es una forma con varios lados para dibujar varios puertos. Lo que importa es la asimetría **adentro / afuera**, no la de arriba / abajo de las capas.

### Estructura de paquetes

```
com.tienda.pedidos
├── dominio
│   └── modelo                 Pedido, LineaPedido, Dinero
├── aplicacion
│   ├── puerto
│   │   ├── entrada            ConfirmarPedido, ConsultarPedido
│   │   └── salida             CargarPedido, GuardarPedido, CobrarPago
│   └── servicio               ConfirmarPedidoService (implementa el puerto de entrada)
└── adaptador
    ├── entrada
    │   └── web                PedidoController
    └── salida
        ├── persistencia       PedidoPersistenceAdapter, PedidoJpaEntity
        └── pagos              PagosHttpAdapter
```

### Código mínimo

```java
// Puerto de entrada
public interface ConfirmarPedido {
    void confirmar(ConfirmarPedidoComando comando);
}

// Puertos de salida: estrechos, uno por necesidad (ISP)
public interface CargarPedido { Optional<Pedido> porId(PedidoId id); }
public interface GuardarPedido { void guardar(Pedido pedido); }

// Aplicación: implementa la entrada y usa las salidas
class ConfirmarPedidoService implements ConfirmarPedido {
    private final CargarPedido cargar;
    private final GuardarPedido guardar;

    public void confirmar(ConfirmarPedidoComando comando) {
        Pedido pedido = cargar.porId(comando.id()).orElseThrow();
        pedido.confirmar();
        guardar.guardar(pedido);
    }
}

// Adaptador primario
@RestController
class PedidoController {
    private final ConfirmarPedido confirmarPedido;

    @PostMapping("/pedidos/{id}/confirmacion")
    void confirmar(@PathVariable String id) {
        confirmarPedido.confirmar(new ConfirmarPedidoComando(new PedidoId(id)));
    }
}

// Adaptador secundario: un adaptador puede implementar varios puertos
@Component
class PedidoPersistenceAdapter implements CargarPedido, GuardarPedido {
    private final PedidoJpaRepository jpa;
    private final PedidoMapper mapper;

    public Optional<Pedido> porId(PedidoId id) { return jpa.findById(id.valor()).map(mapper::aDominio); }
    public void guardar(Pedido pedido) { jpa.save(mapper.aEntidad(pedido)); }
}
```

### Lo que hace posible

- **Probar la aplicación entera sin infraestructura:** adaptadores secundarios en memoria y las pruebas como un adaptador primario más.
- **Varios canales para la misma lógica:** REST, mensajería y un trabajo por lotes llaman al mismo puerto.
- **Desarrollo en paralelo:** se puede construir el negocio antes de elegir la base de datos.
- **Sustituir tecnología por adaptador:** migrar de un proveedor a otro es escribir un adaptador.

## Hexagonal frente a Clean frente a Onion

Las tres comparten la regla "las dependencias apuntan al dominio". Difieren en el énfasis:

| | Énfasis |
|---|---|
| Hexagonal | La frontera adentro/afuera y el vocabulario de puertos y adaptadores. No prescribe la estructura interior. |
| Onion | Los anillos internos: modelo de dominio, servicios de dominio, servicios de aplicación. |
| Clean | Sintetiza las anteriores; añade el círculo de casos de uso y la regla de dependencia explícita. |

En la práctica los equipos mezclan los términos. Lo que debe quedar claro es la regla, no la etiqueta.

## Errores comunes

- **Puertos que copian la tecnología:** `interface PedidoJpaPort { Page<PedidoEntity> findAll(Pageable p); }` no aísla nada.
- **Un puerto de entrada por caso de uso sin que nadie lo sustituya:** si solo hay una implementación y un consumidor, la interfaz de entrada puede sobrar.
- **Lógica de negocio en los adaptadores:** validaciones de reglas en el controlador o en el mapeador.
- **El modelo de dominio se filtra a la API:** serializar `Pedido` directamente a JSON ata el contrato externo al modelo interno.
- **Hexágono dentro de hexágono dentro de un microservicio de 200 líneas.**

## Trade-offs

- A favor: testabilidad alta; independencia de tecnología; simetría de canales; frontera clara para el equipo.
- En contra: mapeo entre modelos; más clases; sobrecosto en servicios simples; requiere disciplina o verificación automática.

## Cuándo no

Servicios CRUD, adaptadores de integración que solo trasladan datos, prototipos. Un criterio útil: ¿hay reglas de negocio que quieras probar sin infraestructura? Si la respuesta es "casi ninguna", el hexágono está vacío.

## Taller

Construye un servicio de "transferencias entre cuentas" con arquitectura hexagonal.

Reglas: saldo suficiente; tope diario por cuenta; no se transfiere a la misma cuenta; toda transferencia queda registrada.

1. Define los puertos de entrada y de salida. Justifica la granularidad de los de salida.
2. Implementa el dominio y el servicio de aplicación sin dependencias externas.
3. Escribe dos adaptadores primarios: REST y línea de comandos. Deben compartir todo salvo la traducción.
4. Escribe dos adaptadores secundarios: en memoria y JPA (o descríbelo).
5. Prueba el caso de uso completo usando solo adaptadores en memoria.
6. Agrega una regla de ArchUnit que haga cumplir la dirección de las dependencias.

Rama `taller/n5-06-hexagonal`.

## Preguntas senior

1. Diferencia entre puerto y adaptador, y entre primario y secundario. Da dos ejemplos de cada uno.
2. ¿Por qué las pruebas son "un adaptador más"? ¿Qué consecuencia tiene para la estrategia de pruebas?
3. ¿Qué granularidad deben tener los puertos de salida? Relaciónalo con ISP.
4. ¿Dónde ubicas las transacciones, la seguridad y la validación en un hexágono?
5. ¿Cuándo el mapeo entre modelo de dominio y modelo de persistencia paga su costo?
6. ¿Cómo migrarías una aplicación en capas a hexagonal de forma incremental?

Siguiente: [07 — Onion Architecture](../07-onion-architecture/).
