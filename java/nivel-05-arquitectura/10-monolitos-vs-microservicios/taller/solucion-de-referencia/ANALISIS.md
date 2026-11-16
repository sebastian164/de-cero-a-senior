# Taller 05-10 — Monolitos frente a microservicios (solución)

El cálculo está en `src/main/java/calculo/CadenaDeServicios.java`, con 2 pruebas (`mvn test`) y una simulación de un millón de peticiones. Salida de `java -cp target/classes calculo.CadenaDeServicios`:

```
Cadena de 5 servicios: disponibilidad 97,5249 % (17,8 h caído al mes), latencia 400 ms
Monolito:              disponibilidad 99,5000 % (3,6 h caído al mes)
Simulación (1.000.000 peticiones): 97,5152 %
```

## 1. Diagnóstico (sistema de referencia: tienda en línea mediana, monolito en capas de 6 años)

| Problema actual | ¿Lo resuelve…? |
|---|---|
| Un cambio en promociones rompe el checkout | Modularidad (fronteras y pruebas de módulo) |
| El despliegue semanal requiere congelar a todos los equipos | Modularidad + despliegue continuo; microservicios solo si persiste con 5+ equipos |
| El reporte de ventas bloquea las escrituras en diciembre | Ninguno: réplica de lectura o CQRS (no es arquitectura de despliegue) |
| El buscador necesita escalar 10 veces más que el resto en campañas | Microservicios (extraer solo el buscador) |
| Pruebas de integración de 40 minutos | Modularidad (probar módulos aislados) |
| Errores 500 por un proveedor de envíos lento | No tiene que ver: tiempos límite y cortacircuitos |
| Nadie entiende el módulo de facturación | No tiene que ver: documentación, dueño claro, refactor |

## 2. Cálculo

- Disponibilidad: todos deben responder, se multiplican: 0,995⁵ = **0,97525 (97,52 %)**. Unas **17,8 horas caído al mes** frente a **3,6 horas** del monolito al 99,5 %: cinco veces más tiempo fuera de servicio.
- Latencia en serie: 5 × 80 = **400 ms** solo de saltos, más los tiempos de red, serialización y reintentos. En el monolito esas cinco llamadas son invocaciones en memoria (microsegundos); queda el trabajo real.
- Para recuperar el 99,5 % con cinco servicios en cadena, cada uno debería estar al 0,995^(1/5) = 99,9 %.

## 3. Diseño

**6 desarrolladores:** monolito modular con un solo despliegue y una base PostgreSQL con esquemas por módulo (`catalogo`, `carrito`, `pedidos`, `pagos`, `envios`, `clientes`). Eventos internos con registro de publicación. Pagos y envíos integran proveedores externos detrás de puertos. Un solo pipeline, observabilidad básica. Razón: la ley de Conway dice que un equipo produce un sistema; no hay a quién dar autonomía.

**80 desarrolladores en 10 equipos:** servicios alineados a capacidades y a equipos: catálogo y búsqueda, precios y promociones, carrito y checkout, pedidos, pagos, logística, clientes, recomendaciones; plataforma (gateway, CI/CD, observabilidad) como equipo propio. Una base por servicio, eventos con outbox, sagas en el checkout, contratos versionados y pruebas de contrato. Se llega extrayendo desde el monolito modular, empezando por lo que más bloquea.

## 4. Extracción paso a paso (módulo `pagos`)

1. **Frontera**: confirmar que nadie accede a sus tablas ni a su interior (Spring Modulith o ArchUnit en verde).
2. **Contrato**: congelar `PagosApi` y sus eventos; escribir pruebas de contrato.
3. **Datos**: mover las tablas de pagos a su propia base. Mientras tanto, doble escritura controlada o replicación (CDC) para que el monolito y el servicio vean lo mismo.
4. **Servicio nuevo**: desplegarlo con el mismo código del módulo; el monolito llama por HTTP detrás de un *feature flag* (Strangler Fig).
5. **Transición sin corte**: tráfico en sombra, luego 1 %, 10 %, 100 %, comparando resultados.
6. **Reversa**: el flag devuelve el tráfico al módulo interno mientras la base del monolito siga sincronizada; solo se apaga el módulo viejo tras semanas estables.
7. **Limpieza**: borrar el código del módulo en el monolito y la sincronización.

## 5. Cinco señales de monolito distribuido

1. Publicar un servicio exige publicar otros a la vez.
2. Varios servicios leen o escriben las mismas tablas.
3. Una petición de usuario atraviesa una cadena síncrona de tres o más servicios.
4. Un cambio de negocio pequeño toca varios repositorios y equipos.
5. Las pruebas de extremo a extremo con todo levantado son la única forma de saber si algo funciona.
