# 07 — Onion Architecture

## Idea sencilla

Una cebolla tiene capas alrededor de un núcleo. Cada capa envuelve a la anterior y se apoya en ella; el núcleo no sabe cuántas capas hay encima. Si quitas la capa exterior, el interior sigue completo.

En esta arquitectura el núcleo es el modelo del negocio, y lo que se puede "pelar" y reemplazar es la tecnología.

## Explicación técnica

Propuesta por Jeffrey Palermo en 2008 como respuesta directa a la arquitectura en capas tradicional, donde todo terminaba dependiendo de la capa de acceso a datos.

```
┌───────────────────────────────────────────────────────┐
│ Infraestructura · Interfaz de usuario · Pruebas       │
│  ┌─────────────────────────────────────────────────┐  │
│  │ Servicios de aplicación                         │  │
│  │  ┌───────────────────────────────────────────┐  │  │
│  │  │ Servicios de dominio                      │  │  │
│  │  │  ┌─────────────────────────────────────┐  │  │  │
│  │  │  │ Modelo de dominio                   │  │  │  │
│  │  │  └─────────────────────────────────────┘  │  │  │
│  │  └───────────────────────────────────────────┘  │  │
│  └─────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────┘
          las dependencias apuntan hacia el centro
```

| Anillo | Contiene | Ejemplo |
|---|---|---|
| Modelo de dominio | Entidades y objetos de valor con su comportamiento | `Pedido`, `Dinero`, `Direccion` |
| Servicios de dominio | Reglas que no pertenecen a una sola entidad; interfaces de repositorio | `PoliticaDeDescuentos`, `RepositorioPedidos` |
| Servicios de aplicación | Casos de uso: orquestan, abren transacciones | `ConfirmarPedido` |
| Exterior | Web, persistencia concreta, mensajería, pruebas | `PedidoController`, `RepositorioPedidosJpa` |

### Sus principios

1. La aplicación se construye alrededor de un modelo de dominio independiente.
2. Los anillos internos definen interfaces; los externos las implementan.
3. La dirección del acoplamiento es hacia el centro.
4. El núcleo compila y funciona sin la infraestructura.

### Lo que la distingue

Frente a hexagonal, Onion da nombre a los anillos interiores. Su aporte más útil es la distinción entre **servicio de dominio** y **servicio de aplicación**:

| | Servicio de dominio | Servicio de aplicación |
|---|---|---|
| Contiene | Reglas de negocio que involucran varios objetos | Coordinación de un caso de uso |
| Conoce | Solo el modelo | El modelo, los repositorios, las transacciones |
| Ejemplo | Calcular si una transferencia entre dos cuentas es permitida | Cargar las cuentas, pedir la transferencia, guardar, publicar el evento |
| Tiene estado | No | No |
| Lo entendería alguien del negocio | Sí | En parte |

```java
// Servicio de dominio: una regla que no pertenece ni a una cuenta ni a la otra
public class ServicioDeTransferencias {
    public void transferir(Cuenta origen, Cuenta destino, Dinero monto, PoliticaDeTopes topes) {
        if (origen.equals(destino)) throw new TransferenciaInvalida("misma cuenta");
        topes.verificar(origen, monto);
        origen.debitar(monto);
        destino.acreditar(monto);
    }
}

// Servicio de aplicación: orquesta; no decide reglas
public class RealizarTransferencia {
    @Transactional
    public void ejecutar(TransferenciaComando comando) {
        Cuenta origen = cuentas.cargar(comando.origen());
        Cuenta destino = cuentas.cargar(comando.destino());
        transferencias.transferir(origen, destino, comando.monto(), topes);
        cuentas.guardar(origen);
        cuentas.guardar(destino);
        eventos.publicar(new TransferenciaRealizada(comando));
    }
}
```

## Errores comunes

- **Servicios de dominio que absorben toda la lógica** y dejan a las entidades como estructuras de datos: es el modelo anémico con otro nombre.
- **Servicios de aplicación con reglas de negocio** (`if (monto > tope)`): la regla debería estar un anillo más adentro.
- **Confundir los anillos con capas apiladas:** en Onion la infraestructura está en el borde y depende del núcleo, no debajo de él.
- **Crear los cuatro anillos por reflejo** cuando el sistema solo tiene dos preocupaciones reales.

## Trade-offs

- A favor: modelo de dominio protegido y expresivo; ubicación clara para cada tipo de lógica; alta testabilidad del núcleo.
- En contra: los límites entre servicio de dominio y de aplicación se discuten en cada equipo; más estructura; sobrecosto si el dominio es pobre.

## Cuándo no

Igual que las anteriores: cuando no hay un dominio con reglas que justifique protegerlo.

## Taller

Usa el dominio de transferencias de la lección anterior o uno de préstamos de biblioteca.

1. Clasifica cada pieza de lógica en uno de los cuatro anillos antes de programar. Justifica las dudosas.
2. Implementa el núcleo (modelo y servicios de dominio) en un módulo o paquete que no tenga ninguna dependencia externa.
3. Identifica una regla que hayas puesto en un servicio de aplicación y debería estar en el dominio, o al revés.
4. Compara tu estructura con la hexagonal de la lección anterior: ¿qué cambió de verdad y qué es solo nombre?
5. Redacta en cinco líneas la convención que le darías a un equipo para decidir entre servicio de dominio y servicio de aplicación.

Rama `taller/n5-07-onion`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Qué problema concreto de la arquitectura en capas quiso resolver Onion?
2. Diferencia entre servicio de dominio y servicio de aplicación con un ejemplo propio.
3. ¿Cuándo una regla debe ir en una entidad y cuándo en un servicio de dominio?
4. ¿Qué tienen en común Onion, hexagonal y Clean, y en qué se diferencian realmente?
5. Un equipo discute una hora sobre en qué anillo va una clase. ¿Qué haces?

Siguiente: [08 — Domain-Driven Design](../08-domain-driven-design/).
