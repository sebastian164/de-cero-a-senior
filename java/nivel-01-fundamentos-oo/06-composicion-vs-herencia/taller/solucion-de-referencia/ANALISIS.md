# Taller — Composición frente a herencia: personajes (solución de referencia)

Carpetas: `1-herencia/` (punto 1) y `2-composicion/` (puntos 3 a 5, con `Pruebas`: 10 pruebas, 0 fallos). En cada una: `javac -d out *.java && java -cp out Main`.

## 1. Tres clases con herencia

`GuerreroConEspada`, `GuerreroConArco` y `MagoConBaston` extienden `Personaje`. Ya con tres aparece la duplicación: el bono de guerrero (`+ 5`) está repetido en dos clases, `defender` es idéntico en las tres y cada daño de arma está escrito dentro de una clase de personaje. Cambiar el daño de la espada obliga a buscar todas las clases "...ConEspada...".

## 2. Cuántas clases con herencia

Una clase por combinación: 4 tipos × 5 armas × 3 armaduras = **60 clases** (salida real de `Main`: `60`). Si además se permite ir sin armadura, son 4 × 5 × 4 = 80. Cada arma nueva suma 4 × 3 = 12 clases; cada tipo nuevo, 15.

## 3. Rediseño con composición

`Personaje` tiene un `TipoPersonaje` (enum con el bono de ataque), un `Arma` y una `Armadura` (records con su dato y, en la armadura, la regla `reducir`). Son 4 tipos y 12 valores (4 + 5 + 3, más `NINGUNA`). `cambiarArma` y `cambiarArmadura` permiten cambiar durante la partida; la prueba "cambio de arma en partida" lo verifica (espada 17 → hacha 19 para un guerrero). Un mago con espada no necesita una clase nueva.

## 4. ¿Queda herencia?

No hay ningún `extends` en `2-composicion/`. Los tipos de personaje, armas y armaduras difieren solo en datos (bono, daño, reducción), así que un valor basta. Si un arma necesitara comportamiento propio (por ejemplo, un arco que hace doble daño a distancia), `Arma` pasaría a ser una interfaz con implementaciones: eso sería implementar un contrato, no heredar código, y seguiría siendo composición desde el punto de vista de `Personaje`. (`enum` y `record` heredan implícitamente de `Enum` y `Record`; es mecánica del lenguaje, no una decisión de diseño.)

## 5. Código de delegación

- `atacar()` delega en `arma.dano()` y `tipo.bonoAtaque()`.
- `defender()` delega en `armadura.reducir(dano)`.
- `describir()` pide nombre a tipo, arma y armadura.
- `cambiarArma` y `cambiarArmadura` son métodos nuevos que la herencia no podía ofrecer.

Son unas cinco líneas de delegación. Es un precio razonable: a cambio se pasa de 60 clases a 4 tipos, las reglas de cada pieza viven en un solo lugar y aparece una capacidad (cambiar de arma en partida) imposible con herencia, porque un objeto no puede cambiar de clase.
