# 05 — Eventos como contrato

## Idea

Un enchufe. Quien fabrica una lámpara no habla con quien cableó tu casa, y aun así encajan, porque los dos respetan una norma. Si un fabricante decide un día ponerle una tercera pata al enchufe "porque es mejor", ninguna lámpara nueva sirve en ninguna casa vieja. Un evento publicado es ese enchufe: una vez que alguien depende de él, ya no es solo tuyo.

## Lo esencial

### Evento, orden y consulta

| Tipo | Dice | Nombre | Quién lo recibe |
|---|---|---|---|
| **Evento** | Algo ya ocurrió | En pasado: `PedidoConfirmado` | Quien quiera; el emisor no los conoce |
| **Orden** (*command*) | Haz esto | En imperativo: `ReservarInventario` | Un destinatario concreto, que puede negarse |
| **Consulta** | Dime esto | — | No va por Kafka |

Un evento no se puede rechazar: es un hecho. Si el nombre de tu "evento" suena a petición (`ActualizarInventario`), es una orden disfrazada, y el emisor en realidad sí depende de quien lo recibe.

### Cuánto dato lleva un evento

| Estilo | Contenido | A favor | En contra |
|---|---|---|---|
| Notificación | Solo el identificador: "el pedido 42 se confirmó" | Pequeño; nunca queda desactualizado | El consumidor tiene que llamar de vuelta al emisor: vuelve el acoplamiento y la carga |
| Con estado | Lo necesario para actuar sin preguntar | El consumidor es autónomo aunque el emisor esté caído | Más grande; hay que cuidar qué datos se exponen |

No es "todo o nada": un evento lleva los datos que sus consumidores razonables necesitan, no la entidad completa volcada.

### El sobre

Todo evento debería traer, además de sus datos:

| Campo | Para qué |
|---|---|
| `id` | Único por evento. Es lo que usa el consumidor para descartar repetidos |
| `tipo` y `version` | Saber qué es y cómo leerlo |
| `ocurridoEn` | Cuándo pasó en el negocio, que no es cuándo se publicó |
| `correlacion` | El identificador de la operación completa, para seguirla por todos los servicios |
| `causa` | El `id` del mensaje que provocó este |

### Evolucionar sin romper

Los eventos viven en el registro días o meses, y productores y consumidores se despliegan por separado. Siempre habrá consumidores viejos leyendo eventos nuevos y consumidores nuevos leyendo eventos viejos.

| Cambio | ¿Rompe? |
|---|---|
| Añadir un campo opcional | No |
| Añadir un valor a una enumeración | Sí, a los consumidores que no contemplan valores desconocidos |
| Quitar un campo | Sí, a quien lo lea |
| Renombrar un campo | Sí. Es quitar uno y añadir otro |
| Cambiar el tipo o el significado de un campo | Sí, y el segundo caso es el peor: no falla, da resultados malos |
| Hacer obligatorio un campo que era opcional | Sí, para los eventos viejos que siguen en el registro |

Reglas que se derivan:

- **El productor solo añade.** Lo que ya publicó, lo mantiene.
- **El consumidor es tolerante:** ignora los campos que no conoce y no falla por valores nuevos.
- Un cambio incompatible es **otro evento**, o una versión nueva en otro tema, con un periodo en que se publican los dos.

### Formato y registro de esquemas

| Formato | A favor | En contra |
|---|---|---|
| JSON | Legible, universal | Sin esquema obligatorio; más grande |
| Avro, Protobuf | Compactos; esquema obligatorio; reglas de compatibilidad comprobables | Necesitan herramientas y un registro de esquemas |

Un **registro de esquemas** guarda las versiones del esquema de cada tema y **rechaza** la publicación de una versión incompatible. Convierte "nos acordamos de no romper" en una comprobación automática.

## Ejemplo

### Antes

```json
{
  "tipo": "pedido_update",
  "data": {
    "id": 42,
    "cliente": { "id": 7, "nombre": "Ana Ruiz", "documento": "1032456789", "correo": "ana@example.com" },
    "estado": 3,
    "items": [{ "producto_id": 9, "cant": 2, "precio": 19900.0 }],
    "creado": "15/11/2026",
    "version_jpa": 12,
    "usuario_modifica": "admin"
  }
}
```

### Análisis

| Problema | Consecuencia |
|---|---|
| `pedido_update` no dice qué pasó | Cada consumidor tiene que comparar con el estado anterior para adivinar si le interesa |
| Es la entidad interna serializada | Cualquier cambio en la base de datos del productor cambia el evento. El modelo interno se volvió contrato público |
| `estado: 3` | Un número mágico que solo entiende el productor |
| Datos personales que ningún consumidor necesita | Quedan copiados en el registro y en cada sistema que lea el tema |
| Sin identificador de evento | El consumidor no puede descartar repetidos |
| Sin versión | No hay forma ordenada de evolucionarlo |
| Fecha sin zona y precio como decimal binario | Ambigüedad y errores de redondeo |

### Después

```json
{
  "id": "0193b2c4-7a1e-7c3a-9f6e-2b1d4c5e6f70",
  "tipo": "PedidoConfirmado",
  "version": 1,
  "ocurridoEn": "2026-11-15T17:42:10Z",
  "correlacion": "compra-8f3a",
  "causa": "0193b2c4-6f02-7b11-8d2a-91c0e4a7b3d5",
  "datos": {
    "pedidoId": "pedido-42",
    "clienteId": "cliente-7",
    "lineas": [{ "productoId": "producto-9", "cantidad": 2, "precioUnitarioCentavos": 1990000 }],
    "totalCentavos": 3980000,
    "moneda": "COP"
  }
}
```

- El nombre dice el hecho.
- Tiene sobre: identificador, versión, momento, correlación y causa.
- Lleva lo que inventario y facturación necesitan para actuar, y nada de datos personales: quien los necesite, que los pida con el `clienteId` y con permiso.
- Dinero en enteros y con moneda; fecha en formato ISO con zona.
- La clave del mensaje en Kafka es `pedido-42`.

## Errores comunes

- Publicar la entidad de la base de datos.
- Eventos genéricos (`Actualizado`, `Cambio`) con un campo que dice qué cambió.
- Órdenes con nombre de evento.
- Cambiar el significado de un campo manteniendo su nombre.
- Consumidores que fallan ante un campo o un valor desconocido.
- Datos personales en temas con retención larga.
- Un solo tema con veinte tipos de evento sin relación, o un tema por cada tipo cuando necesitan orden entre sí.
- Versionar "por si acaso" y acabar manteniendo cinco versiones vivas.

## Taller

**Objetivo:** diseñar los eventos de una tienda y demostrar que puedes cambiarlos sin romper a nadie.

El dominio que usarás hasta el final de la ruta: una tienda con cuatro servicios, **Pedidos**, **Pagos**, **Inventario** y **Envíos**.

1. Lista los hechos del negocio desde que el cliente confirma la compra hasta que recibe el paquete, incluidos los caminos tristes (pago rechazado, sin existencias, envío fallido). Dales nombre en pasado.
2. Para cada uno, decide: qué servicio lo publica, en qué tema, con qué clave y qué datos lleva. Justifica la clave pensando en qué tiene que llegar en orden.
3. Define el sobre común y escribe el esquema de tres eventos (JSON Schema, Avro o Protobuf).
4. Revisa cada evento con esta pregunta: ¿un consumidor puede actuar sin llamar de vuelta al productor? Ajusta.
5. Marca qué campos contienen datos personales. Decide qué haces con cada uno y qué harías si un cliente pide que borren sus datos y están en un tema con retención de un año.
6. Publica eventos de la versión 1 y escribe un consumidor para ellos.
7. Evoluciona un evento añadiendo un campo opcional. Demuestra que el consumidor viejo sigue funcionando con eventos nuevos y que un consumidor nuevo funciona con eventos viejos que siguen en el tema.
8. Ahora haz un cambio incompatible (renombra un campo). Muestra cómo falla el consumidor viejo. Después diseña y ejecuta la migración correcta sin detener a nadie.
9. Añade un valor nuevo a una enumeración. ¿Tu consumidor lo tolera? Corrígelo si no.
10. Levanta un registro de esquemas (elige uno y sigue su documentación), registra tus esquemas con una regla de compatibilidad e intenta registrar el cambio del paso 8. Guarda el rechazo.
11. Escribe una prueba de contrato: una prueba del consumidor que falle si el productor publica algo que el consumidor no puede leer.

**Criterios de aceptación**

- Cada evento tiene dueño, tema, clave y esquema.
- Ningún evento es la entidad interna.
- Los pasos 7 a 9 se demuestran ejecutando, con productores y consumidores de versiones distintas a la vez.
- `ANALISIS.md` explica la regla de evolución que seguirá tu equipo en media página.

**Entrega:** rama `taller/kafka-05`, con los esquemas, el código en `taller/solucion/` y `ANALISIS.md`.

## En el trabajo real

En cuanto un tema tiene consumidores de otros equipos, cambiarlo es una negociación. Por eso los equipos maduros tratan los eventos como una API pública: con dueño, documentación, registro de esquemas, comprobación de compatibilidad en la integración continua y un proceso para retirarlos.

## Preguntas senior

1. Diferencia entre evento y orden, y por qué importa para el acoplamiento.
2. ¿Notificación o evento con estado? Argumenta con un caso.
3. ¿Qué cambios en un evento son compatibles y cuáles no?
4. ¿Cómo haces un cambio incompatible sin corte?
5. ¿Qué aporta un registro de esquemas que no aporte la disciplina del equipo?
6. ¿Cómo tratas los datos personales en un registro que no permite borrar un mensaje?
