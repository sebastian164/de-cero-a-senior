# Taller SRP — Registro de usuarios

## Punto de partida

En [`inicio/`](inicio/) está `RegistroUsuarios`, una clase que registra usuarios en una aplicación. Ejecútala antes de cambiar nada:

```bash
cd inicio
javac *.java
java Main
```

Funciona y está razonablemente encapsulada. Tu trabajo es decidir si viola SRP, dónde, y qué hacer al respecto.

## Contexto del negocio

En esta empresa hay cuatro áreas que opinan sobre el registro:

- **Producto** define qué datos se piden y qué hace válido a un usuario.
- **Seguridad** define la política de contraseñas y cómo se protegen.
- **Marketing** define los mensajes que recibe el usuario.
- **Cumplimiento** define qué eventos deben quedar registrados para auditoría.

Además, el equipo de **plataforma** decide dónde se almacenan los datos.

## Parte 1 — Análisis (antes de escribir código)

Crea `solucion/ANALISIS.md` con:

1. Una tabla de actores: qué actor, qué cambio realista pediría y qué líneas del método `registrar` tendría que tocar.
2. Tres escenarios concretos en los que un cambio pedido por un actor podría romper algo que le importa a otro.
3. Qué necesitarías montar hoy para escribir una prueba automática que verifique solo la política de contraseñas.

## Parte 2 — Refactorización

En `solucion/`, escribe una versión que cumpla esto:

- Cada actor puede recibir su cambio tocando una clase que no le importa a los demás.
- El comportamiento visible del programa no cambia: la salida debe ser la misma (salvo la hora de la auditoría). El `Main` puede cambiar para ensamblar las piezas nuevas.
- Queda claro en qué clase vive el flujo del registro.

Restricciones:

- Java puro, sin librerías.
- No introduzcas interfaces todavía, salvo que puedas justificar por qué las necesitas hoy. Depender de abstracciones es tema de DIP.

## Parte 3 — Los tres cambios

Sin escribir el código, indica en `ANALISIS.md` qué clase o clases de **tu** solución habría que tocar para cada cambio:

1. Seguridad exige contraseñas de 12 caracteres con al menos un símbolo.
2. Marketing quiere que el correo de bienvenida incluya un código de descuento.
3. Cumplimiento exige auditar también los intentos de registro rechazados.

Si alguno te obliga a tocar más de una clase, explica por qué y si eso revela un problema en tu diseño.

## Parte 4 — Defensa

1. ¿Qué dejaste junto a propósito, aunque se pudiera separar? ¿Por qué?
2. ¿Cuántas clases quedaron? ¿Alguna te parece excesiva?
3. Tu solución tiene al menos un punto débil. ¿Cuál es?
4. Tus respuestas a las preguntas de entrevista de la sección 8 de la lección.

## Entrega

Rama `taller/01-srp` y pull request contra `main`. La revisión técnica se hace ahí.

## Si te atascas

Pide una pista, no la solución.
