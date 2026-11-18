# Taller 12-05: comunicación y mentoría (solución)

Los puntos que piden hablar con otra persona (grabar, recoger objeciones, probar con alguien) no se pueden ejecutar sin un interlocutor: aquí se entregan los materiales y las objeciones anticipadas, y esa parte queda declarada como no comprobada con una persona real. Lo que sí se puede ejecutar (el README del punto 5) se ejecuta.

## 1. Defensa de diez minutos del taller integrador del nivel 5 (mensajería urbana)

| Min | Contenido |
|---|---|
| 0-1 | **Problema y criterios.** 2.000 envíos/día, pico de 10.000 en diciembre, equipo de 7, 4 meses, presupuesto limitado. Criterios en orden: evolución del dominio, costo operativo, disponibilidad de la asignación, trazabilidad de pagos. |
| 1-4 | **Propuesta.** Monolito modular con contextos: Envíos, Cotización, Asignación, Seguimiento, Facturación. Hexagonal en los dos núcleos (Asignación y Facturación). Diagrama y recorrido de un envío de punta a punta. |
| 4-6 | **Alternativas descartadas.** Microservicios (siete personas, cuatro meses: el costo operativo supera el beneficio); un monolito sin módulos (la expansión a tres ciudades lo vuelve inmanejable); comprar el seguimiento a un tercero (decisión: comprar mapas, construir asignación, porque es el núcleo). |
| 6-8 | **Costos y riesgos que declaro yo.** Un único despliegue acopla los ritmos de cambio; la asignación en tiempo real necesita carga medida; dependencia de dos proveedores externos; la expansión puede forzar separar Asignación. |
| 8-10 | **Evidencia y siguiente paso.** Medición de las estrategias en el nivel 11 (reserva sin sobreventa con 1.000 hilos), plan de *spike* sobre asignación, criterio para extraer un servicio. |

**Tres objeciones anticipadas y respuesta:**
1. "¿Por qué no microservicios si vamos a crecer?" → el crecimiento previsto es de tres ciudades, no de veinte equipos; el costo de operar microservicios hoy es más alto que el de extraer uno después, y los contextos ya están delimitados para poder hacerlo.
2. "¿Qué pasa en diciembre con 5 veces la carga?" → la capacidad se calcula con el método del nivel 11 (el pico de 10.000 envíos diarios es unos 0,12 envíos/s promedio y decenas en la hora pico); el cuello es la asignación, no la API.
3. "¿Y si el proveedor de mapas cae?" → degradación: se asigna sin ETA exacta; la integración vive tras un puerto y se puede cambiar.

## 2. La misma propuesta para dos audiencias

**Equipo de desarrollo:**

> Propongo extraer la liquidación semanal a un proceso por lotes con idempotencia. Hoy corre dentro de la transacción de entrega y bloquea conexiones del pool cuando la pasarela tarda. Alternativas: subir el timeout (no ataca la causa); caché de tarifas (reduce, no elimina). Costo: la liquidación aparecerá con segundos de retraso y hay que escribir el consumidor idempotente con la tabla de eventos procesados.

**Gerencia:**

> Esta semana, 30 entregas quedaron sin cobrar durante una hora porque un cálculo bloquea el cierre de la entrega mientras espera a otro sistema. Proponemos separarlos: la entrega se cierra de inmediato y la liquidación se calcula segundos después. Son dos semanas de trabajo y elimina esa causa. El único cambio visible: la liquidación tarda unos segundos en aparecer en los reportes.

## 3. Mentoría: inversión de dependencias sin dar la definición

Esto es un **guion y un conjunto de hipótesis a comprobar**, no el registro de una sesión real: no hubo con quién ejecutarla. Se escribe como se usaría y con la forma de evaluarlo después.

Guion de preguntas (de la más concreta a la más abstracta). Se elige una clase que de verdad depende de un detalle de infraestructura: `ControladorArchivos`, que construye y usa un `S3Client` del SDK de AWS directamente (comprobable en `servicio-pedidos/src/main/java/pedidos/ControladorArchivos.java`).

1. "Abre `ControladorArchivos`. ¿Qué pasa con la subida de archivos si mañana dejamos S3 y usamos el disco de un servidor?" (Se espera que descubra que el controlador importa clases del SDK de AWS.)
2. "Si quisieras probar ese controlador sin AWS ni LocalStack, ¿qué necesitarías?" (Se espera que llegue a una interfaz.)
3. "¿Quién debería ser dueño de esa interfaz: el controlador o el adaptador de S3?" (Se espera que llegue a la inversión; sin dibujo quizá no.)
4. "Dibuja las flechas de dependencia antes y después. ¿Qué cambió?"

**Hipótesis a comprobar en la sesión real** (anotar lo que ocurra de verdad, sin adornarlo): (a) la pregunta 2 funcionará mejor que la 1, porque el dolor de las pruebas es concreto; (b) la 3 necesitará el dibujo; (c) si a los 15 minutos la persona no ha dicho "interfaz" por sí misma, se pasa al ejemplo guiado en vez de seguir preguntando. Registro previsto: pregunta hecha, respuesta literal, si la persona avanzó sola, qué se cambió para la siguiente vez.

## 4. Tres retroalimentaciones (situación, comportamiento, impacto)

1. **Situación:** en la revisión del jueves del PR de migraciones. **Comportamiento:** dejaste cuatro comentarios con el motivo y una propuesta cada uno. **Impacto:** pude corregirlo en una hora sin llamada; sigue así.
2. **Situación:** en el incidente del martes. **Comportamiento:** reiniciaste el servicio sin avisar al canal. **Impacto:** perdimos 15 minutos tratando de entender por qué cambió el estado; la próxima vez, un mensaje de una línea antes.
3. **Situación:** en la demostración a producto. **Comportamiento:** explicaste el cambio de la cola sin jerga y con un ejemplo de pedido. **Impacto:** producto aprobó la prioridad en la reunión en lugar de pedir otra.

## 5. README que permite arrancar sin ayuda

El README es el archivo [`servicio-pedidos/README.md`](servicio-pedidos/README.md) (junto al servicio que describe, con su `compose.yaml`), y **los pasos se ejecutan de verdad** con `./verificar-readme.sh`: extrae del propio `README.md` los comandos (no hay copias), levanta con `docker compose up --build`, espera a que aparezca el arranque, ejecuta el `curl` y comprueba que el pedido llega a PostgreSQL, ejecuta `./mvnw verify`, y provoca el fallo de la sección "Si algo falla" para comprobar que el mensaje es el que promete. Dos diferencias con una persona nueva: el compose arranca en segundo plano y el puerto del anfitrión se puede cambiar con `PUERTO_APP` (el remedio que el propio README da para "puerto ocupado").

```markdown
# pedidos

Servicio de pedidos (Spring Boot 4, Java 21).

## Arrancar en local (5 minutos)
1. Requisitos: JDK 21, Docker.
2. `docker compose up --build`  (aplicación en :8080, PostgreSQL con comprobación de salud).
3. Espera a que el registro de `app` muestre `Started` (unos segundos) y prueba: `curl -XPOST localhost:8080/api/pedidos -H 'Content-Type: application/json' -d '{"clienteId":1,"total":10}'`.

## Pruebas
`./mvnw verify`

## Configuración
Todo por variables de entorno: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`. Sin secretos en el repositorio.

## Si algo falla
- "no password was provided" o "password authentication failed": falta `SPRING_DATASOURCE_PASSWORD` o no coincide con la de la base.
- Puerto 8080 ocupado: cambiar el mapeo en `compose.yaml`.
```

Lo que la automatización **no** sustituye: "comprobarlo con alguien" (una persona nueva) sigue sin hacerse. El script sí encontró dos defectos del README al ejecutarlo: el paso 3 fallaba si se ejecutaba justo después del paso 2 (la aplicación tarda unos segundos en arrancar; se añadió la espera) y el mensaje de error real sin contraseña es "no password was provided", no "password authentication failed" (se añadieron los dos). Además, al ejecutar los pasos del README de la sesión 10-02 se encontró que su `compose.yaml` no pasaba usuario ni contraseña; se corrigió en ese README.

## 6. Balance del curso

Ejemplo de balance: recorrido de `PROGRESO.md` (raíz del repositorio) completo: 102 filas en 10 secciones, **todas todavía en blanco (☐)**: ninguna marcada como en curso ni aprobada. Este balance no puede decir "dominado" de nada que no se haya marcado; lo que sí puede decir es qué se ha ejecutado y qué falta practicar.

| Sección de PROGRESO.md | Filas | Qué sé hoy | Plan |
|---|---|---|---|
| Ruta Java y Spring Boot | 13 | Niveles 10 a 12 resueltos y ejecutados en local (contenedores, Kubernetes en kind, CI/CD con act, IaC con LocalStack, prototipos de system design); los niveles 1 a 9 no se repasaron al escribir este ejemplo | Recorrer 1 a 9 con sus talleres y marcar la casilla solo con la solución ejecutada |
| Ruta Angular | 8 | Sin empezar; sin marcar | Un nivel cada dos semanas después de terminar Java |
| Ruta React y Next.js | 8 | Sin empezar; sin marcar | Igual que Angular, intercalando por proyecto |
| Talleres de Docker y Kubernetes | 16 | Los fundamentos de 10-02 y 10-03 se ejecutaron; los 16 talleres a fondo no | Hacerlos en orden; el 10-03 ya cubre sondas y apagado |
| Arquitectura hexagonal con Java | 8 | Sin marcar; el nivel 5 de Java es el prerrequisito | Después del nivel 5; aplicarla al servicio `pedidos` |
| Talleres de Kafka y sagas | 15 | Sin marcar; el servicio `pedidos` ya publica en Kafka (10-02) | Kafka 01 a 04 antes de las sagas |
| Talleres de HikariCP | 6 | Sin marcar; en 11-02 se vio el pool como límite posible | HikariCP 01 y 04 con las mediciones de 11-02 |
| Talleres de NoSQL | 7 | Sin marcar; Redis se usó para sesiones (10-05, 11-02) | NoSQL 05 y 06 con ese mismo Redis |
| Talleres de WebFlux | 8 | Sin marcar | Solo si el criterio de "cuándo no" del taller 01 lo justifica |
| Talleres de prácticas (TDD, Karate, SDD, ODD) | 13 | Sin marcar; se trabajó con pruebas, no con TDD | TDD 01 a 03 con el servicio `pedidos`; ODD cuando haya tableros (deuda 4 de 12-04) |

**Plan a seis meses:** (1) mes 1: Java niveles 1 a 9 hasta que todas las casillas lleven una solución ejecutada; (2) meses 2 a 3: Docker/Kubernetes y hexagonal sobre `pedidos`; (3) meses 4 a 5: Kafka, sagas y NoSQL; (4) mes 6: una ruta de frontend; (5) en paralelo, un ADR por decisión irreversible, mentoría semanal a una persona (el guion del punto 3) y una revisión de PR por semana con la guía de 12-03. Cada casilla se marca con la fecha y la prueba que la respalda en la Bitácora.
