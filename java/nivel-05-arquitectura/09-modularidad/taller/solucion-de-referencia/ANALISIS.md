# Taller 05-09 — Modularidad: plataforma de cursos como monolito modular (solución)

Dominio: la plataforma de cursos del taller integrador del Nivel 3. Código en `src/main/java/cursos`, verificado con Spring Modulith 2.1.2 sobre Spring Boot 4.1.1. `mvn test` ejecuta 4 pruebas y genera los diagramas PlantUML en `target/spring-modulith-docs/`.

## 1. Módulos

| Módulo | Responsabilidad | API pública | Datos propios |
|---|---|---|---|
| `catalogo` | Cursos, módulos, lecciones, precios base | `CatalogoApi` (título, estructura, precio base) | cursos, módulos, lecciones |
| `promociones` | Reglas de descuento y cupones | `PromocionesApi.precioFinal(curso, cupon)` | promociones, cupones, usos de cupón |
| `inscripciones` | Ciclo de vida de la inscripción | `InscripcionesApi`, eventos `InscripcionActivada`, `CursoCompletado` | inscripciones y su historial de estados |
| `pagos` | Cobro con pasarelas por país, reembolsos | `PagosApi.cobrar(...)`, evento `PagoAprobado` | transacciones, conciliación |
| `aprendizaje` | Entrega de lecciones, progreso, exámenes guardados | `AprendizajeApi`, evento `LeccionCompletada` | progreso, intentos de examen, dispositivos |
| `certificacion` | Emisión y verificación de certificados | `CertificacionApi` | certificados |

## 2. Grafo de dependencias (sin ciclos)

```
promociones ──► catalogo ◄── aprendizaje
                   ▲              │ (evento LeccionCompletada)
certificacion ─────┘              ▼
     ▲                       inscripciones ──► catalogo
     │ (evento CursoCompletado)   │ (llamada) 
     └────────────────────────────┤
                                  ▼
pagos ── (evento PagoAprobado) ──► inscripciones
```

Ciclo que apareció en el primer borrador: `inscripciones → pagos` (para cobrar al inscribir) y `pagos → inscripciones` (para activar al aprobar). Se rompió invirtiendo una dirección con un evento: `pagos` publica `PagoAprobado` y no conoce a `inscripciones`; `inscripciones` llama a `PagosApi` y escucha el evento. Spring Modulith detecta ciclos como este; `detectaAccesoAlInteriorYCiclos` lo comprueba con una aplicación mal hecha (`cursosmal`).

## 3. Dos interacciones

- **Llamada directa**: `certificacion` → `CatalogoApi.tituloDe`. Es una consulta que se necesita ya, para imprimir el certificado, y el catálogo es estable.
- **Evento**: `inscripciones` publica `CursoCompletado` y `certificacion` lo escucha con `@ApplicationModuleListener`. Completar un curso no debe fallar si la emisión del certificado falla, y mañana estadísticas y recomendaciones se suscriben sin tocar `inscripciones`. Implementado y probado en `EventoEntreModulosTest` con el registro de publicación de eventos en JDBC (H2).

## 4. Reporte con datos de tres módulos (ventas por curso con progreso medio y precio cobrado)

| Opción | Cómo | Costos |
|---|---|---|
| Composición por API | El módulo de reportes llama a `CatalogoApi`, `InscripcionesApi` y `PagosApi` y une en memoria | Simple y sin datos duplicados; lento y con varias consultas para volúmenes grandes; acopla el reporte a tres APIs |
| Proyección de lectura propia | `reportes` escucha `InscripcionActivada`, `PagoAprobado` y `LeccionCompletada` y mantiene su tabla desnormalizada | Consultas rápidas, nadie toca tablas ajenas; consistencia eventual, más código y reconstrucción si cambia el esquema |

Para el panel de administración se elige la proyección; para un reporte mensual poco usado basta la composición.

## 5. Esqueleto y verificación automática

Implementados tres módulos (`catalogo`, `inscripciones`, `certificacion`), cada uno con su API en el paquete raíz y su implementación en `interno`.

- `ModulosTest.laEstructuraModularSeRespeta`: `ApplicationModules.of(PlataformaCursosApplication.class).verify()`, el fragmento del README, pasa y reconoce exactamente tres módulos.
- `ModulosTest.detectaAccesoAlInteriorYCiclos`: sobre `cursosmal` la verificación falla con "Cycle detected" y con el acceso a `reportes.interno.TablaReportes`.
- `ModulosTest.generaDocumentacion`: escribe `components.puml` y un diagrama por módulo.

## 6. Primer módulo a extraer

`pagos`. Tiene integraciones externas por país, requisitos de cumplimiento (PCI) y un ritmo de cambios ligado a las pasarelas, no al producto. La condición real: que un segundo equipo se haga cargo de pagos y que los despliegues comunes empiecen a bloquear cambios urgentes de una pasarela, o que una auditoría exija aislar el manejo de datos de pago. Sin una de esas condiciones, extraerlo solo agrega red y fallos parciales.
