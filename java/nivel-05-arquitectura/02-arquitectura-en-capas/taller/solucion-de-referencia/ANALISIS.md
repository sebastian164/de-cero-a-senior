# Taller 05-02 — Arquitectura en capas (solución)

Aplicación con dos entidades relacionadas (`Cliente` 1—N `Pedido`) y una regla no trivial: **un pedido solo se confirma si la deuda vigente del cliente (pedidos confirmados o despachados) más su total no supera el cupo del cliente**.

- `tienda.antes`: capas por tipo técnico (`controller`, `service`, `repository`, `model`, `dto`), tal como suele empezar.
- `tienda.despues`: paquetes por funcionalidad (`clientes`, `pedidos`) con capas dentro y la regla en la entidad.

`mvn test` ejecuta 7 pruebas: la regla antes (con Mockito), la regla después (sin nada), la API con H2 (200, 422 y 404) y dos reglas de ArchUnit.

## 1. Dependencias entre paquetes (versión `antes`)

```
controller ──► service ──► repository ──► model
    │                          ▲            ▲
    └──────────────────────────┘            │
    └───────────────────────────────────────┘   (el controlador devuelve la entidad)
dto  (huérfano: nadie lo usa)
```

Todas las flechas terminan en `repository` y `model`: la persistencia y el esquema son el centro.

## 2. Problemas encontrados en `antes`

| Problema | Dónde |
|---|---|
| Capa de paso | `PedidoService.buscar` solo reenvía a `findById` |
| Entidad expuesta en la API | `PedidoController.buscar` y `confirmar` devuelven `model.Pedido` (y su `Cliente` con cupo incluido) |
| Controlador que se salta el servicio | `PedidoController.buscar` usa `PedidoRepository` directamente |
| Lógica de negocio en el controlador | validación `id <= 0` mezclada con el canal |
| Lógica en consultas SQL | `PedidoRepository.deudaDe` decide qué estados son deuda (`'CONFIRMADO', 'DESPACHADO'`) dentro del JPQL |
| Modelo anémico | `model.Pedido` solo tiene getters y setters; la regla del cupo está en `PedidoService.confirmar` |

## 3. La regla pasó del servicio a la entidad

- Antes (`ReglaDeCupoAntesTest`): para probar la regla hubo que simular `PedidoRepository` con Mockito, programar `findById`, `deudaDe` y `save`, y llamar al servicio. La prueba depende de los nombres de los métodos de persistencia.
- Después (`ReglaDeCupoDespuesTest`): `new Pedido(...).confirmar(deuda)`. Tres casos en 25 líneas, sin dobles. Qué estados son deuda pasó también al dominio (`EstadoPedido.cuentaComoDeuda`): la consulta ya no decide reglas, recibe los estados.

## 4. Agregar un campo (`fechaEntrega`, visible en la API)

| Organización | Paquetes que se tocan |
|---|---|
| Por tipo técnico (con DTO usado como se debe) | `model` (entidad), `dto` (respuesta), `service` (mapeo), `controller` (si recibe el valor): **4** |
| Por funcionalidad | `pedidos` (entidad y `PedidoResponse`): **1** |

## 5. Regla de ArchUnit

```java
noClasses().that().haveSimpleNameEndingWith("Controller")
        .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository");
```

`ReglasDeArquitecturaTest` la ejecuta: pasa sobre `tienda.despues` y falla sobre `tienda.antes`, y el mensaje de error nombra `PedidoController` y `PedidoRepository`.
