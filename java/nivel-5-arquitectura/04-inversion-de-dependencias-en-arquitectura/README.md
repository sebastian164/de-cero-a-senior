# 04 — Inversión de dependencias aplicada a arquitectura

Requisito: [DIP](../../nivel-2-solid/05-dip/). Esta lección es el puente entre la arquitectura en capas y las arquitecturas centradas en el dominio (Clean, hexagonal, Onion).

## Idea sencilla

Una empresa define sus políticas: "todo proveedor debe entregar factura electrónica y garantía de un año". Los proveedores se adaptan a esa política. La empresa no reescribe sus procesos cada vez que cambia de proveedor.

Lo estable y valioso (la empresa, sus reglas) dicta el contrato. Lo reemplazable (los proveedores) se acomoda. En software: el negocio dicta el contrato; la base de datos, el framework y las APIs externas se acomodan.

## Explicación técnica

En capas tradicionales, las dependencias siguen el flujo de control: presentación → negocio → persistencia. El negocio queda apoyado sobre la tecnología.

Aplicar DIP en la frontera entre negocio e infraestructura cambia la dirección de esa dependencia:

```
En capas:                         Con la dependencia invertida:

  Presentación                      Presentación        Infraestructura
       │                                 │                     │
       ▼                                 ▼                     ▼
    Negocio                    ┌─────────────────────────────────────┐
       │                       │               Negocio               │
       ▼                       │  (define las interfaces que usa)    │
  Persistencia                 └─────────────────────────────────────┘
```

Ahora **todas las dependencias de código apuntan hacia el negocio**. Es la regla que comparten Clean Architecture, hexagonal y Onion. Lo que las distingue son nombres y detalles; la idea es esta.

### Flujo de control frente a dependencia de código

Son cosas distintas, y confundirlas es el error más frecuente al estudiar este tema:

- En ejecución, el caso de uso **llama** al repositorio JPA (el control va de negocio a infraestructura).
- En el código fuente, el repositorio JPA **importa** la interfaz del negocio, y el negocio no importa nada de JPA (la dependencia va de infraestructura a negocio).

La interfaz es lo que permite que ambas direcciones sean opuestas.

### Qué es un detalle

Desde el punto de vista del negocio, son detalles: la base de datos, el framework web, el ORM, el proveedor de mensajería, el formato de la API, la interfaz de usuario. Importan mucho operativamente, pero las reglas del negocio no deberían cambiar porque uno de ellos cambie.

### La prueba práctica

Abre las clases del negocio y mira sus `import`. Si aparecen `jakarta.persistence`, `org.springframework.web`, `com.fasterxml.jackson` o el SDK de un proveedor, la dependencia no está invertida.

## Antes: el negocio depende de la infraestructura

```java
package com.tienda.pedidos.service;

import com.tienda.pedidos.repository.PedidoJpaRepository;   // infraestructura
import com.tienda.pagos.StripeClient;                        // SDK de un proveedor
import org.springframework.kafka.core.KafkaTemplate;         // mensajería concreta

@Service
public class PedidoService {
    private final PedidoJpaRepository repositorio;
    private final StripeClient stripe;
    private final KafkaTemplate<String, String> kafka;
    // Para probar una regla hacen falta una base, Stripe y Kafka (o simular sus clases).
}
```

## Después: la infraestructura depende del negocio

```java
// ---- Negocio: no importa nada técnico ----
package com.tienda.pedidos.dominio;

public interface RepositorioPedidos {            // el negocio dice qué necesita
    Pedido guardar(Pedido pedido);
    Optional<Pedido> buscar(PedidoId id);
}

public interface PasarelaPagos {
    ResultadoPago cobrar(Dinero monto, MedioDePago medio);
}

public interface PublicadorEventos {
    void publicar(EventoDeDominio evento);
}

// ---- Infraestructura: implementa los contratos del negocio ----
package com.tienda.pedidos.infraestructura;

@Repository
class RepositorioPedidosJpa implements RepositorioPedidos { ... }

@Component
class PasarelaStripe implements PasarelaPagos { ... }

@Component
class PublicadorKafka implements PublicadorEventos { ... }
```

Las interfaces hablan el idioma del negocio (`cobrar`, no `createPaymentIntent`) y viven en su paquete. Los tipos que cruzan la frontera (`Pedido`, `Dinero`, `ResultadoPago`) también son del negocio.

## Qué exige y qué cuesta

- **Mapeo.** Si la entidad de dominio no es la entidad JPA, hay que traducir entre ambas. Es el costo principal y el que más se discute.
- **Más tipos.** Una interfaz y un adaptador por cada frontera.
- **Un ensamblador.** Alguien conecta implementaciones con interfaces; Spring lo hace.
- **Disciplina.** Nada impide que alguien importe JPA en el dominio. Hay que verificarlo con pruebas de arquitectura o con módulos de compilación separados.

## Trade-offs

- A favor: el negocio se prueba sin infraestructura; la tecnología se puede cambiar o actualizar por partes; las decisiones técnicas se pueden posponer; varios adaptadores pueden convivir (REST y mensajería, base real y en memoria).
- En contra: indirección y mapeo; excesivo cuando el "negocio" es mover datos de un formulario a una tabla; riesgo de abstraer mal y terminar con interfaces que son un calco del proveedor.

## Cuándo no invertir

- Aplicaciones CRUD sin reglas: el dominio es el esquema, e invertir no protege nada.
- Dependencias estables y ubicuas (el lenguaje, `java.time`, colecciones).
- Cuando acoplarse al framework es una decisión consciente y aceptada (por ejemplo, usar Spring Data directamente en un servicio pequeño).

## Taller

1. En un proyecto tuyo en capas, lista los `import` de una clase de servicio y clasifícalos: negocio, lenguaje, framework, infraestructura.
2. Elige una dependencia de infraestructura y aplícale la inversión: define la interfaz en el paquete del negocio, con vocabulario del negocio, y mueve la implementación.
3. Dibuja el antes y el después con flechas de dependencia de código (no de llamadas).
4. Escribe una prueba del servicio que use una implementación en memoria.
5. Escribe una regla de ArchUnit: "las clases de `..dominio..` no dependen de `..infraestructura..` ni de `org.springframework..`". Si no puedes ejecutarla, descríbela con precisión.

Rama `taller/n5-04-dip-arquitectura`.

## Preguntas senior

1. Explica con un dibujo la diferencia entre flujo de control y dependencia de código.
2. ¿Por qué la interfaz debe vivir del lado del negocio? ¿Qué pasa si vive junto a la implementación?
3. ¿Usar la entidad JPA como entidad de dominio rompe la inversión? ¿Cuándo aceptas ese compromiso?
4. ¿Cómo impides que el equipo viole la regla de dependencias con el tiempo?
5. ¿Qué decisiones técnicas permite posponer esta inversión y por qué eso tiene valor?

Siguiente: [05 — Clean Architecture](../05-clean-architecture/).
