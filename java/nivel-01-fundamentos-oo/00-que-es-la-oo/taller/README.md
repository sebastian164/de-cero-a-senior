# Taller 0 — Un pedido que se defiende solo

## Punto de partida

En [`inicio/`](inicio/) hay un pequeño sistema de pedidos. Compílalo y ejecútalo antes de cambiar nada:

```bash
cd inicio
javac *.java
java Main
```

Funciona. Tu trabajo es explicar por qué eso no basta y arreglarlo.

## Reglas del negocio

1. Un pedido siempre tiene cliente y su subtotal nunca es negativo.
2. Un pedido nace en estado creado. Solo un pedido creado se puede pagar y solo un pedido pagado se puede enviar.
3. Los clientes VIP tienen 10 % de descuento y los empleados 30 %. Los regulares no tienen descuento.
4. Intentar una operación inválida no puede pasar desapercibido.

## Parte 1 — Diagnóstico (antes de escribir código)

Crea `solucion/ANALISIS.md` y responde:

1. Lista todos los problemas de diseño que encuentres. Para cada uno indica qué podría salir mal en un sistema real.
2. ¿Cuáles de las cuatro reglas del negocio puede violar hoy el código sin que nada falle? Demuéstralo con una línea de código para cada una.
3. Llega un requisito nuevo: clientes mayoristas con 15 % de descuento. ¿Qué archivos y qué métodos tendrías que modificar en la versión actual?

## Parte 2 — Refactorización

En `solucion/`, escribe una nueva versión que cumpla esto:

- Es imposible crear un pedido inválido.
- Es imposible llevar un pedido a un estado inválido desde afuera.
- Cada regla del negocio está escrita en un único lugar.
- Agregar el cliente mayorista no obliga a modificar la clase del pedido.
- Un `Main` demuestra los casos válidos y al menos tres intentos inválidos rechazados.

Restricciones:

- Java puro, sin librerías.
- No uses herencia para resolver los descuentos. Todavía no hemos estudiado cuándo es correcto heredar.

## Parte 3 — Defensa

Añade a `ANALISIS.md`:

1. Qué decisiones tomaste y por qué.
2. Qué alternativa consideraste y descartaste.
3. Qué parte de tu solución te parece discutible o sobrediseñada.
4. Tus respuestas a las preguntas del paso 9 de la lección.

## Entrega

Rama `taller/00-oo` y pull request contra `main`. La revisión técnica se hace ahí.

## Si te atascas

Pide una pista, no la solución. Una buena pista responde a "¿qué pregunta debería estar haciéndome?".
