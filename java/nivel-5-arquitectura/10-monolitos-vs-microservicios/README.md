# 10 — Monolitos frente a microservicios

## Idea sencilla

Un restaurante con una sola cocina es fácil de administrar: un chef ve todo, un proveedor, una nevera. Una cadena con veinte cocinas especializadas en ciudades distintas puede atender mucha más gente y una cocina puede cerrar sin que cierren las demás, pero necesita logística, transporte, estándares y gerentes. Nadie abre veinte cocinas para vender cincuenta almuerzos al día.

## Explicación técnica

| | Monolito | Microservicios |
|---|---|---|
| Qué es | Una unidad de despliegue | Varios servicios pequeños, desplegables por separado |
| Comunicación interna | Llamadas en memoria | Red: HTTP, gRPC, mensajes |
| Datos | Una base, transacciones ACID | Una base por servicio, consistencia eventual |
| Despliegue | Todo junto | Independiente por servicio |
| Escalado | La aplicación completa | Por servicio |
| Fallos | Si cae, cae todo | Aislables, si se diseña para ello |
| Operación | Simple | Compleja: orquestación, observabilidad, redes |

### Qué es un microservicio de verdad

- Se despliega **de forma independiente**: publicar una versión no obliga a coordinar con otros.
- Es **dueño de sus datos**: nadie más lee ni escribe su base.
- Está organizado alrededor de una **capacidad del negocio**.
- Lo mantiene **un equipo** que responde por él de principio a fin.

Si para publicar un servicio hay que publicar otros tres a la vez, o varios comparten base de datos, no son microservicios: es un **monolito distribuido**, que tiene los costos de ambos mundos y las ventajas de ninguno.

### Lo que los microservicios cuestan

Al cruzar la red, lo que era trivial deja de serlo:

- **Una llamada puede fallar, tardar o duplicarse.** Hacen falta tiempos límite, reintentos, idempotencia y cortacircuitos.
- **No hay transacciones entre servicios.** Hacen falta sagas y compensaciones.
- **Depurar exige trazas distribuidas.**
- **Las pruebas de extremo a extremo son lentas y frágiles.** Se sustituyen por pruebas de contrato.
- **Infraestructura:** contenedores, orquestador, descubrimiento, configuración, secretos, despliegue continuo por servicio.
- **Versionado de contratos:** cambiar una API exige compatibilidad hacia atrás.
- **Costo:** más máquinas, más herramientas, más personas dedicadas a plataforma.

### Lo que los microservicios dan

Resuelven sobre todo un problema **organizacional**: permiten que muchos equipos entreguen sin pisarse. Los beneficios técnicos (escalado y despliegue independientes, aislamiento de fallos, libertad tecnológica) son reales, pero rara vez son la razón principal.

**Ley de Conway:** las organizaciones producen sistemas cuya estructura copia su estructura de comunicación. La arquitectura y el organigrama se diseñan juntos.

### El camino recomendado

1. Empieza con un **monolito modular**. Al principio no se conocen bien las fronteras del dominio, y moverlas dentro de un monolito es un refactor; entre servicios es una migración.
2. Extrae un servicio cuando haya una **razón concreta**: un módulo con necesidades de escalado muy distintas, un equipo bloqueado por el ritmo de despliegue común, requisitos de aislamiento o de tecnología.
3. Extrae **por las fronteras de módulo** que ya existen y están verificadas.

### Cómo decidir

| Señal | Inclina hacia |
|---|---|
| Un equipo de hasta unas diez personas | Monolito |
| Dominio nuevo, fronteras inciertas | Monolito |
| Varias decenas de desarrolladores bloqueándose entre sí | Microservicios |
| Partes con cargas muy distintas | Extraer esas partes |
| Sin experiencia operando sistemas distribuidos | Monolito |
| Necesidad fuerte de consistencia transaccional | Monolito |
| Despliegue continuo y observabilidad ya maduros | Microservicios son viables |

## El problema: microservicios por moda

```
Usuario → Gateway → servicio-pedidos → servicio-clientes → servicio-direcciones
                          │                                        │
                          └──────────────► base de datos compartida ◄┘
```

Tres saltos de red síncronos para armar una respuesta, una base compartida, y un equipo de cuatro personas manteniendo doce repositorios. Si `servicio-direcciones` se cae, no se puede consultar un pedido. Latencia sumada, disponibilidad multiplicada a la baja, y cada cambio de esquema rompe varios servicios.

## La alternativa

El mismo sistema como monolito modular con tres módulos: una llamada en memoria, una transacción, un despliegue. Si mañana el módulo de pedidos necesita escalar aparte, su frontera ya está definida.

## Trade-offs

Ninguna opción es superior. El monolito optimiza simplicidad y consistencia; los microservicios, autonomía de equipos y escalado independiente, a cambio de complejidad distribuida. La pregunta útil no es "¿cuál es mejor?", sino "¿qué problema tengo hoy que justifique pagar ese precio?".

## Taller

1. **Diagnóstico.** Toma un sistema que conozcas. Lista sus problemas actuales y clasifica cada uno: ¿lo resolverían los microservicios, lo resolvería la modularidad o no tiene que ver con la arquitectura?
2. **Cálculo.** Un flujo atraviesa cinco servicios en cadena, cada uno con disponibilidad del 99,5 % y latencia de 80 ms. Calcula la disponibilidad y la latencia del flujo. Compara con un monolito al 99,5 %.
3. **Diseño.** Para una plataforma de comercio electrónico con 6 desarrolladores, propón la arquitectura inicial. Repite para la misma empresa con 80 desarrolladores en 10 equipos.
4. **Extracción.** Elige un módulo y describe paso a paso cómo lo extraerías a un servicio: datos, contratos, transición sin corte, reversa.
5. **Detección.** Escribe cinco señales que te permitan reconocer un monolito distribuido.

Rama `taller/n5-10-monolito-microservicios`.

## Preguntas senior

1. ¿Qué problema resuelven principalmente los microservicios? ¿Es técnico u organizacional?
2. ¿Qué es un monolito distribuido y cómo se llega a él?
3. Explica la ley de Conway y cómo la usarías a tu favor.
4. ¿Por qué empezar con un monolito aunque se prevea crecer?
5. ¿Cómo divides una base de datos compartida entre dos servicios sin detener la operación?
6. ¿Qué capacidades operativas deben existir antes de adoptar microservicios?
7. Te piden "migrar a microservicios". ¿Qué preguntas haces antes de responder?

Siguiente: [11 — Patrones arquitectónicos](../11-patrones-arquitectonicos/).
