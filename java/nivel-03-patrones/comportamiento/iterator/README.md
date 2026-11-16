# Iterator

Familia: comportamiento. Frecuencia en Java empresarial: constante en el uso (cada `for-each`), baja en la implementación propia.

## El problema

Quieres recorrer los elementos de una colección sin conocer cómo está guardada por dentro: si es un arreglo, una lista enlazada, un árbol, páginas traídas de una base de datos o líneas de un archivo. Si el cliente recorre con índices o conoce la estructura, queda acoplado a ella.

## Cómo reconocer que lo necesitas

- Una clase propia que agrupa elementos y que los clientes quieren recorrer con `for-each`.
- Datos que llegan por partes (paginación, flujos) y no caben o no conviene cargarlos completos.
- Varios órdenes de recorrido sobre la misma estructura.

## Antes

```java
// El cliente conoce la paginación del repositorio
for (int pagina = 0; ; pagina++) {
    List<String> lote = repositorio.pagina(pagina, 100);
    if (lote.isEmpty()) break;
    for (String cliente : lote) procesar(cliente);
}
```

## Después

<!-- file: ejemplo/Main.java -->
```java
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class Main {

    public static void main(String[] args) {
        ClientesPaginados clientes = new ClientesPaginados(new FuenteSimulada(), 2);
        for (String cliente : clientes) {          // el cliente no sabe que hay páginas
            System.out.println(cliente);
        }
    }
}

class FuenteSimulada {
    private final List<String> datos = List.of("Ana", "Luis", "Marta", "Pedro", "Sofía");

    List<String> pagina(int numero, int tamano) {
        System.out.println("  (consulta página " + numero + ")");
        int desde = Math.min(numero * tamano, datos.size());
        int hasta = Math.min(desde + tamano, datos.size());
        return datos.subList(desde, hasta);
    }
}

/** Implementar Iterable permite usar for-each y oculta la paginación. */
class ClientesPaginados implements Iterable<String> {

    private final FuenteSimulada fuente;
    private final int tamanoPagina;

    ClientesPaginados(FuenteSimulada fuente, int tamanoPagina) {
        this.fuente = fuente;
        this.tamanoPagina = tamanoPagina;
    }

    @Override
    public Iterator<String> iterator() {
        return new Iterator<>() {
            private int pagina = 0;
            private Iterator<String> actual = fuente.pagina(0, tamanoPagina).iterator();

            @Override
            public boolean hasNext() {
                if (actual.hasNext()) {
                    return true;
                }
                actual = fuente.pagina(++pagina, tamanoPagina).iterator();
                return actual.hasNext();
            }

            @Override
            public String next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return actual.next();
            }
        };
    }
}
```

Los datos se traen bajo demanda: si el cliente deja de recorrer, no se consultan más páginas.

## Alternativas

- **Devolver una colección** (`List`), si los datos caben en memoria. Es lo normal.
- **`Stream`**: recorrido interno, perezoso y componible. Para procesar datos suele ser preferible a un iterador propio.
- **Programación reactiva o cursores de base de datos**, para volúmenes grandes con contrapresión.

## En Spring y en Java

- `Iterable`, `Iterator`, `Spliterator` y todo `for-each`.
- `Page` y `Slice` de Spring Data; métodos de repositorio que devuelven `Stream<T>` (requieren transacción abierta y cerrarse).
- `ItemReader` de Spring Batch: lee un elemento a la vez de archivos o consultas.
- `ResultSet` de JDBC y `Scanner` son iteradores con otro nombre.

## Trade-offs

- A favor: oculta la estructura interna; permite recorrido perezoso; un mismo cliente recorre cualquier fuente.
- En contra: escribir un iterador correcto tiene sus detalles (`hasNext` debe poder llamarse varias veces sin efectos visibles); modificar la colección mientras se recorre lanza `ConcurrentModificationException`; los iteradores sobre recursos externos deben cerrarse.

## Errores comunes

- `hasNext()` que avanza el cursor de forma visible.
- Olvidar `NoSuchElementException` en `next()`.
- Eliminar de una lista dentro de un `for-each` (usa `removeIf` o `Iterator.remove`).
- Iteradores perezosos que retienen una conexión abierta indefinidamente.

## Cuándo no usarlo

Casi nunca hace falta implementarlo: las colecciones y los streams ya lo traen. Hazlo solo para estructuras propias o fuentes perezosas.

## Taller

1. Implementa `RangoFechas` (inicio, fin) que sea `Iterable<LocalDate>` y permita `for (LocalDate dia : rango)`.
2. Agrega un segundo recorrido: solo días hábiles (lunes a viernes).
3. Reescribe ambos con `Stream` (`datesUntil`). ¿Cuál preferirías mantener?
4. Demuestra `ConcurrentModificationException` con una lista y corrígelo de dos formas.
5. Explica el riesgo de devolver un `Stream` desde un repositorio de Spring Data sin `@Transactional`.

Entrega: rama `taller/n3-iterator`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).
