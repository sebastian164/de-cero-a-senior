# Taller SRP — Registro de usuarios (solución de referencia)

Ejecución: `javac -d out *.java && java -cp out Main && java -cp out Pruebas` (8 pruebas, 0 fallos).
Si ejecutas el `Main` del código de inicio (`../inicio/`) y el de esta solución y comparas las dos salidas, ignorando la hora de la auditoría, son idénticas.

## Parte 1 — Análisis

### 1. Actores (líneas de `taller/inicio/RegistroUsuarios.java`)

| Actor | Cambio realista | Líneas de `registrar` |
|---|---|---|
| Producto | Pedir teléfono; aceptar solo correos corporativos; nombre con al menos dos palabras. | 16-21 (validaciones de datos), firma del método (14) |
| Seguridad | 12 caracteres y un símbolo; bcrypt o Argon2 con sal en lugar de SHA-256. | 22-27 (política), 32-39 (hash) |
| Marketing | Otro texto, asunto, código de descuento, plantilla HTML. | 44-50 |
| Cumplimiento | Auditar rechazos; agregar IP; formato JSON; otro destino. | 53 |
| Plataforma | Guardar en una base de datos en lugar del `HashMap`; otra normalización del correo. | 11 (campo), 28 (búsqueda), 42 (guardar), `totalUsuarios` |

### 2. Tres escenarios en que un actor rompe a otro

1. **Plataforma rompe a Producto.** Cambian `usuarios.put(correo.toLowerCase(), ...)` (línea 42) por un repositorio que guarda el correo tal cual, pero la búsqueda de la línea 28 sigue con `toLowerCase()`: `LAURA@correo.example` deja de detectarse como duplicado y se registra dos veces el mismo usuario.
2. **Seguridad rompe a Cumplimiento.** Para "no filtrar datos" alguien de seguridad elimina el correo de los mensajes del método y toca la línea 53, que comparte bloque y variables: la auditoría pierde el correo que cumplimiento está obligado a conservar.
3. **Marketing rompe a todos.** Para enviar el correo con un servicio externo agregan una llamada de red en las líneas 44-50; si el servicio falla y lanza excepción, el usuario ya quedó guardado (línea 42) pero no se audita (línea 53): un registro sin rastro de auditoría, y el `Main` informa error aunque el usuario existe.

### 3. Qué hay que montar hoy para probar solo la política de contraseñas

Una instancia completa de `RegistroUsuarios` y una llamada a `registrar` con nombre y correo válidos, porque la política está en medio del método. Para los casos válidos la prueba además guarda en el mapa, imprime el correo y la auditoría en la consola (hay que capturar `System.out` para no ensuciar la salida) y depende de la hora (`LocalDateTime.now()`). No hay forma de distinguir "rechazado por contraseña" de "rechazado por correo" salvo comparando textos de mensajes. No hay forma de probar el hash sin leer el campo privado.

## Parte 2 — Refactorización

| Clase | Actor | Contenido |
|---|---|---|
| `ValidadorDatosUsuario` | Producto | Nombre y correo. |
| `PoliticaContrasenas` | Seguridad | Reglas de la contraseña y cómo se protege (hash). |
| `AlmacenUsuarios` | Plataforma | El mapa y la normalización del correo como clave. |
| `CorreoBienvenida` | Marketing | Texto y envío simulado del correo. |
| `Auditoria` | Cumplimiento | El evento `REGISTRO_USUARIO` y su formato. |
| `RegistroUsuarios` | Dueño del proceso | El flujo: validar, comprobar duplicado, guardar, avisar, auditar. Es la clase donde vive el registro. |

El orden de las validaciones se mantuvo idéntico (nombre, correo, longitud, número, duplicado), así que los mensajes de rechazo y la salida no cambian. Sin interfaces: hoy hay una sola implementación de cada pieza y nada que intercambiar (eso es DIP). `Main` ensambla las piezas. `Pruebas` verifica la política de contraseñas sin almacén, correo ni auditoría, que era lo imposible en la Parte 1.

## Parte 3 — Los tres cambios

1. **12 caracteres con al menos un símbolo**: solo `PoliticaContrasenas.validar`.
2. **Código de descuento en el correo**: solo `CorreoBienvenida` (si el código lo genera otro sistema, `CorreoBienvenida` lo pediría a ese sistema; `RegistroUsuarios` no cambia).
3. **Auditar rechazos**: `Auditoria` (método nuevo `registroRechazado(correo, motivo)`) y `RegistroUsuarios` (envolver las validaciones en un `try/catch` que audite y relance). Son dos clases, y es correcto: auditar un rechazo cambia el flujo (qué pasa cuando falla una validación), y el flujo es responsabilidad de `RegistroUsuarios`. No revela un problema de diseño; revela que "qué se audita" es parte del proceso y "cómo se audita" es de cumplimiento.

## Parte 4 — Defensa

1. **Qué quedó junto a propósito.** La regla de la contraseña y su hash, en `PoliticaContrasenas`: ambas son de Seguridad y cambian juntas (pasar a bcrypt suele venir con nuevas reglas). Y el texto del correo junto a su envío simulado: con un envío real se separaría el canal (`EnviadorCorreo`, Plataforma) del contenido (Marketing).
2. **Cuántas clases.** Seis más `Main`, frente a dos. `ValidadorDatosUsuario` es la más discutible: dos `if`. Se justifica porque Producto es el actor que más cambia en un registro, pero si nadie la ha tocado en un año, se puede devolver a `RegistroUsuarios`.
3. **Punto débil.** La comprobación de duplicado vive en el flujo pero depende de cómo `AlmacenUsuarios` normaliza el correo; además no es atómica (comprobar y guardar son dos llamadas), así que con concurrencia o una base de datos real podría registrar duplicados. La solución real sería una restricción única en el almacén.
4. **Preguntas de entrevista (sección 8).**
   1. "Una sola cosa" se aplica a funciones; SRP habla de una sola razón para cambiar, es decir, de un solo actor. Un `CalculadoraNomina` con veinte métodos (horas extra, recargos nocturnos, festivos, deducciones) hace muchas cosas pero todas responden a la norma laboral que define Recursos Humanos: cumple SRP.
   2. No. No formatea, no guarda ni notifica: coordina a quienes lo hacen. Su única razón para cambiar es que cambien los pasos del proceso. Violaría SRP si contuviera cómo se formatea o cómo se envía.
   3. (1) Escribir pruebas de caracterización por la API pública que fijen el comportamiento actual, sin refactorizar nada. (2) Identificar los actores con el historial de git (qué métodos cambian juntos y quién los pide) y extraer, con "extraer clase" y delegación, el grupo más independiente; la clase vieja delega en la nueva para que las quince dependientes no cambien. (3) Mover a los consumidores uno por uno a la clase nueva, en pull requests pequeños, y borrar la delegación al final (patrón estrangulador a nivel de clase).
   4. No necesariamente. Si responden a actores distintos, unificarlos acopla contabilidad con logística: un cambio pedido por uno altera al otro. La tensión es con DRY. La duplicación accidental (mismo código, distintas razones) se deja; la duplicación real (misma regla) se unifica.
   5. Un microservicio es SRP a escala de despliegue: debería responder ante un actor o dominio. Cuando varios equipos piden cambios al mismo servicio, aparecen colas de cambios, despliegues coordinados y conflictos de prioridad: es el "servicio dios" con red de por medio.
   6. Que dividió por número de métodos y no por actores. Le pediría nombrar el actor y un cambio realista de cada clase; si varias responden al mismo actor y cambian juntas, hay que volver a juntarlas. Nueve clases de un método fragmentan una sola responsabilidad y destruyen la cohesión.
   7. Con el historial de git (qué archivos y métodos cambian juntos, quién los cambia y qué tickets cita cada commit), los tickets y quién los reporta, y entrevistas a los usuarios del sistema. También se agrupan los métodos por las dependencias que usan: grupos que no comparten dependencias suelen ser actores distintos.
