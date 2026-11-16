# Taller — Clases y objetos: la biblioteca (solución de referencia)

Ejecución: `javac -d out *.java && java -cp out Main && java -cp out Pruebas` (11 pruebas, 0 fallos).

## 1. Problemas de diseño del código de inicio

| Problema | Qué puede fallar |
|---|---|
| Tres arreglos paralelos (`titulos`, `autores`, `disponibles`) unidos solo por el índice | Agregar un libro en un arreglo y olvidarlo en otro desalinea los datos: el autor de un libro queda pegado a otro, o aparece `ArrayIndexOutOfBoundsException`. |
| Estado `static` | Hay una sola biblioteca posible por programa. Dos sedes compartirían inventario sin quererlo. |
| Se trabaja con índices (`prestar(0)`) | Un índice no dice qué libro es; pasar el índice equivocado presta otro libro sin error. |
| Nadie protege la cantidad de ejemplares | `disponibles[i] = -7` es posible desde cualquier lugar. |
| `devolver` no valida nada | Ver punto 4: crea ejemplares de la nada. |
| El fallo de `prestar` se imprime y sigue | Quien llama no sabe si el préstamo ocurrió. |
| No existe el total de ejemplares | Sin ese dato es imposible validar una devolución. |

## 2. Rediseño

- `Libro`: título, autor, ejemplares totales y disponibles; `prestar()` y `devolver()` protegen la invariante `0 <= disponibles <= ejemplares`.
- `Biblioteca`: un catálogo propio (lista de `Libro`), busca por título y delega. No hay `static` fuera de `main` (la clase `Pruebas` es el arnés de pruebas y no forma parte de la solución).

## 3. Dos bibliotecas independientes

`Main` crea `Centro` y `Norte`, ambas con "Cien años de soledad". Prestar en Centro deja 1 disponible allí y 2 en Norte (salida real: `Centro - Cien años de soledad (García Márquez): 1` y `Norte - Cien años de soledad (García Márquez): 2`). La prueba "B es independiente" lo verifica.

## 4. El error de `devolver`

`devolver` suma sin comprobar que haya algo prestado. En el código de inicio, `devolver(1)` sobre Rayuela (0 ejemplares, préstamo rechazado) deja 1 disponible: la salida real del inicio muestra `Rayuela (Cortázar): 1`, un ejemplar que no existe.

La regla vive en `Libro.devolver()`, porque es una invariante del libro (no puede haber más disponibles que ejemplares) y solo el libro conoce ambos números. Si viviera en `Biblioteca`, cualquier otro código que tenga un `Libro` podría saltársela.

## 5. Referencia y objeto

```java
Libro ficciones = new Libro("Ficciones", "Borges", 1);
Libro otraReferencia = ficciones;   // segunda referencia al mismo objeto
otraReferencia.prestar();
ficciones.disponibles();            // 0: el préstamo se hizo sobre el único objeto
Libro copia = new Libro("Ficciones", "Borges", 1);
ficciones == copia;                 // false: mismos datos, otro objeto
```

`new` crea un objeto en memoria; una variable solo guarda una referencia a él. Asignar una variable a otra copia la referencia, no el objeto. Salida real: `ficciones == otraReferencia: true`, `ficciones == copia: false`.
