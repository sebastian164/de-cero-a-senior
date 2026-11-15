# Proyectos de aplicación — ruta Java

Los talleres de cada lección practican un concepto. Estos proyectos obligan a combinarlos en aplicaciones completas. Son seis, de dificultad creciente, y cada uno se apoya en los niveles indicados.

Reglas comunes:

- Cada proyecto vive en su propio repositorio o en una carpeta `proyectos/<nombre>/solucion/`.
- Lleva un `README` con cómo ejecutarlo, un `DECISIONES.md` con las decisiones de diseño y sus alternativas, y pruebas desde el proyecto 3.
- No hay solución de referencia. La revisión se hace sobre tu entrega.
- Antes de programar, escribe qué vas a construir y qué dejas fuera.

## 1. Gestor de biblioteca en consola

**Después de:** Niveles 1 y 2. **Tecnología:** Java puro.

Una aplicación de línea de comandos para una biblioteca pequeña.

- Catálogo de libros con varios ejemplares; socios; préstamos y devoluciones.
- Reglas: máximo tres préstamos por socio; no se presta a quien tiene multas; multa por día de retraso; lista de espera para libros agotados.
- Los datos se guardan en archivos para sobrevivir al cierre del programa.

**Se evalúa:** objetos que protegen sus reglas, ausencia de estado global, separación entre reglas, almacenamiento y consola, y que cambiar el archivo por otro almacenamiento no toque las reglas.

**Extensión:** un segundo tipo de material (revistas) con reglas distintas de préstamo, sin modificar las clases existentes.

## 2. Motor de precios y promociones

**Después de:** Nivel 3. **Tecnología:** Java puro.

Una biblioteca que calcula el precio final de un carrito.

- Productos sueltos y combos (que pueden contener combos).
- Promociones combinables: porcentaje, valor fijo, 2×1, descuento por volumen, cupón, envío gratis sobre un monto.
- Reglas de combinación: cuáles se acumulan, cuáles son excluyentes, en qué orden se aplican.
- Impuestos por categoría.
- El resultado explica cómo se llegó al precio: qué promoción aplicó y cuánto descontó.

**Se evalúa:** que agregar una promoción sea agregar una clase; que el orden de aplicación esté definido en un solo lugar; y que cada patrón usado se justifique por un problema real.

**Extensión:** reglas de promoción definidas como datos, cargadas de un archivo.

## 3. Billetera digital con pruebas

**Después de:** Nivel 4. **Tecnología:** Java, Maven, JUnit, AssertJ.

El núcleo de una billetera, desarrollado con TDD.

- Cuentas con saldo, depósitos, retiros y transferencias.
- Topes diarios, comisiones por tipo de operación, bloqueo por intentos fallidos.
- Historial de movimientos inmutable.
- Todo lo que dependa del tiempo debe poder probarse con un reloj controlado.

**Se evalúa:** el historial de commits (rojo, verde, refactor), pruebas que verifican comportamiento y no implementación, casos límite, manejo de errores con excepciones de dominio, y cobertura de mutación si la mides.

**Extensión:** agrega conversión de moneda con una tasa obtenida de un servicio externo simulado.

## 4. API de reservas con arquitectura hexagonal

**Después de:** Niveles 5 y 7. **Tecnología:** Spring Boot, PostgreSQL, Testcontainers.

Un servicio para reservar espacios (salas, canchas o consultorios).

- Disponibilidad por franjas, reservas, cancelaciones con política de plazos, lista de espera.
- Nunca dos reservas en la misma franja, tampoco con peticiones simultáneas.
- API REST con validación, errores uniformes, paginación, idempotencia y documentación OpenAPI.
- Seguridad: los usuarios solo ven y modifican lo suyo; los administradores, todo.
- El dominio no depende de Spring ni de JPA, y eso se verifica con una prueba de arquitectura.

**Se evalúa:** dirección de las dependencias, reglas probadas sin infraestructura, pruebas de integración con base real, una prueba de concurrencia que demuestre que no hay doble reserva, y tres ADR.

**Extensión:** un segundo adaptador de entrada (mensajería o línea de comandos) que reutilice los casos de uso sin cambios.

## 5. Tienda como monolito modular con eventos

**Después de:** Niveles 5, 8 y 9. **Tecnología:** Spring Boot, Spring Modulith o ArchUnit, PostgreSQL, un broker.

Una tienda en línea organizada en módulos: catálogo, inventario, pedidos, pagos y notificaciones.

- Cada módulo es dueño de sus tablas y expone una API pública.
- Confirmar un pedido reserva inventario, cobra y notifica. Si el cobro falla, el inventario se libera.
- Los módulos reaccionan a eventos; la publicación es confiable (Outbox) y los consumidores son idempotentes.
- Migraciones con Flyway. Índices justificados con planes de ejecución.
- Resiliencia frente a la pasarela de pagos simulada: tiempo límite, reintentos y cortacircuitos.

**Se evalúa:** que no haya dependencias cíclicas ni acceso a tablas ajenas, que el flujo quede consistente ante fallos en cualquier paso, y la justificación de qué es síncrono y qué va por eventos.

**Extensión:** extrae un módulo a un servicio independiente y documenta todo lo que tuviste que cambiar.

## 6. Proyecto final: plataforma desplegada y defendida

**Después de:** Niveles 10 a 12.

Lleva el proyecto 5 (u otro de complejidad equivalente) a un estado de producción.

- Imagen de contenedor optimizada, configuración externa y secretos fuera del repositorio.
- *Pipeline* de integración y entrega continuas con pruebas, análisis y despliegue automático.
- Desplegado en un clúster local o en la nube, con sondas, recursos y apagado ordenado.
- Infraestructura definida como código.
- Métricas, registros estructurados y trazas; un tablero y dos alertas con su procedimiento.
- Una prueba de carga con los resultados y el cuello de botella encontrado.
- Documento de diseño del sistema con estimaciones, diagrama y compromisos.

**Entrega final:** una defensa de quince minutos del sistema: el problema, la arquitectura, las tres decisiones más importantes con sus alternativas, lo que harías distinto y cómo evolucionaría con diez veces la carga.

**Se evalúa:** lo mismo que evalúa una entrevista o una revisión de arquitectura real: que puedas diseñar, implementar, probar, operar y defender una solución con criterio.
