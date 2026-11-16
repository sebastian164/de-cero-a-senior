# 09 — Modularidad

## Idea sencilla

Un edificio de apartamentos comparte estructura, ascensor y acueducto, pero cada apartamento tiene su puerta. Los vecinos no entran a tu cocina a tomar un vaso: tocan el timbre. Si remodelas tu baño, los demás no se enteran.

Un sistema modular es un solo edificio (un solo despliegue) con apartamentos bien cerrados (módulos) que se comunican por la puerta (una interfaz pública).

## Explicación técnica

Un **módulo** es una unidad de código con tres propiedades:

1. **Alta cohesión:** agrupa lo que pertenece a una misma capacidad del negocio.
2. **Interfaz pública pequeña y explícita:** lo que otros pueden usar.
3. **Interior oculto:** todo lo demás es privado y puede cambiar sin avisar.

La modularidad no depende de cuántos procesos se despliegan. Un monolito puede ser modular y un conjunto de microservicios puede ser un enredo.

### El monolito modular

Un único desplegable, organizado en módulos con fronteras verificadas.

```
com.tienda
├── pedidos
│   ├── PedidosApi              ← lo único público del módulo
│   ├── PedidoConfirmado        ← evento público
│   └── interno                 ← nadie de afuera puede importar esto
│       ├── dominio
│       ├── aplicacion
│       └── infraestructura
├── inventario
│   ├── InventarioApi
│   └── interno
└── facturacion
    ├── FacturacionApi
    └── interno
```

Dentro de cada módulo puede haber capas, hexágono o lo que el módulo necesite. Módulos distintos pueden tener arquitecturas internas distintas.

### Cómo se comunican los módulos

| Mecanismo | Acoplamiento | Uso |
|---|---|---|
| Llamada a la API pública del otro módulo | Directo, síncrono | Consultas, operaciones que deben ocurrir ya |
| Eventos (`PedidoConfirmado`) | Indirecto: el emisor no conoce al receptor | Consecuencias en otros módulos |
| Datos compartidos | Altísimo y oculto | Evitar |

### Las reglas que mantienen la modularidad

1. **Sin dependencias cíclicas** entre módulos.
2. **Nadie accede al interior de otro módulo.**
3. **Cada módulo es dueño de sus datos.** Un módulo no hace `JOIN` ni consulta directa sobre las tablas de otro. Esta es la regla que más se viola y la que más cuesta arreglar después.
4. **Las transacciones no cruzan módulos** si se quiere poder separarlos algún día.

### Cómo hacerlas cumplir

Las convenciones se erosionan. Se necesita verificación automática:

| Herramienta | Qué da |
|---|---|
| Visibilidad de paquete de Java | Clases internas sin `public` |
| Módulos de Maven o Gradle | El compilador impide dependencias no declaradas |
| ArchUnit | Reglas de arquitectura como pruebas |
| **Spring Modulith** | Verifica fronteras y ciclos, prueba módulos aislados, eventos entre módulos con registro de publicación, y genera documentación |
| JPMS (`module-info.java`) | Encapsulamiento fuerte a nivel de plataforma |

```java
@Test
void laEstructuraModularSeRespeta() {
    ApplicationModules.of(TiendaApplication.class).verify();   // Spring Modulith
}
```

## El problema: la gran bola de lodo

```java
package com.tienda.facturacion;

@Service
class FacturacionService {
    @Autowired PedidoRepository pedidoRepository;          // repositorio interno de otro módulo
    @Autowired InventarioService inventarioService;        // que a su vez usa FacturacionService

    void facturar(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId).get();
        pedido.setEstado("FACTURADO");                     // modifica la entidad de otro módulo
        // ...
    }
}
```

Facturación manipula el interior de pedidos, hay un ciclo con inventario, y cualquier cambio en `Pedido` puede romper la facturación. Todo está acoplado con todo.

## La versión modular

```java
// API pública de pedidos
public interface PedidosApi {
    ResumenPedido resumen(PedidoId id);
}

// Facturación reacciona a un hecho; no manipula pedidos
@ApplicationModuleListener
void alConfirmar(PedidoConfirmado evento) {
    ResumenPedido resumen = pedidos.resumen(evento.pedidoId());
    facturas.emitirPara(resumen);
}
```

## Trade-offs

- A favor: equipos que trabajan en paralelo con pocos conflictos; cambios locales; un módulo se puede extraer a un servicio si de verdad hace falta; se conserva la simplicidad operativa del monolito.
- En contra: definir bien las fronteras es difícil y equivocarse es caro; la comunicación por API o eventos es más laboriosa que un `JOIN`; exige herramientas y disciplina.

## Cuándo no

En aplicaciones pequeñas con un solo equipo, tres o cuatro paquetes bien nombrados bastan. La modularidad formal empieza a pagar cuando hay varias capacidades de negocio y más de un equipo, o cuando el código ya muestra enredo.

## Taller

Diseña como monolito modular una plataforma de cursos (usa el dominio del taller integrador del Nivel 3).

1. Propón de cuatro a seis módulos. Para cada uno: responsabilidad, API pública y datos de los que es dueño.
2. Dibuja el grafo de dependencias. No puede tener ciclos; si aparece uno, resuélvelo y explica cómo.
3. Elige dos interacciones entre módulos: una por llamada directa y otra por evento. Justifica.
4. Un reporte necesita datos de tres módulos. ¿Cómo lo resuelves sin un `JOIN` entre sus tablas? Da dos opciones con sus costos.
5. Implementa el esqueleto de paquetes de dos módulos con sus API y describe (o escribe) la verificación automática de fronteras.
6. ¿Qué módulo extraerías primero a un servicio independiente y qué condición real tendría que darse?

Rama `taller/n5-09-modularidad`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Qué diferencia hay entre modularidad y distribución?
2. ¿Por qué "cada módulo es dueño de sus datos" es la regla más importante y la más difícil?
3. ¿Cómo rompes una dependencia cíclica entre dos módulos? Da tres técnicas.
4. ¿Qué papel tienen los eventos en un monolito modular y qué garantías de entrega necesitas?
5. ¿Cómo decides los límites de los módulos? Relaciónalo con contextos delimitados.
6. Un monolito modular bien hecho, ¿qué ventajas concretas conserva frente a microservicios?

Siguiente: [10 — Monolitos frente a microservicios](../10-monolitos-vs-microservicios/).
