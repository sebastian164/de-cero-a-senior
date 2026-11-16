package genericos;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenericosTest {

    // 1. Resultado<T>
    @Test
    void resultadoMapYOrElse() {
        Resultado<Integer> ok = Resultado.exito(20);
        Resultado<Integer> mal = Resultado.fallo("sin datos");
        assertThat(ok.map(n -> n * 2).orElse(0)).isEqualTo(40);
        assertThat(mal.map(n -> n * 2).orElse(0)).isZero();
        Resultado<String> texto = ok.map(Object::toString);         // Function<Object, String> sirve gracias a ? super T
        assertThat(texto).isEqualTo(Resultado.exito("20"));
        assertThat(mal.map(Object::toString)).isEqualTo(Resultado.fallo("sin datos"));
    }

    // 2. Cache<K, V>
    @Test
    void cacheExpulsaElMasAntiguo() {
        Cache<String, Integer> cache = new Cache<>(2);
        cache.poner("a", 1);
        cache.poner("b", 2);
        cache.obtener("a");                     // leer no cambia la antigüedad (orden de inserción)
        cache.poner("c", 3);
        assertThat(cache.obtener("a")).isEmpty();
        assertThat(cache.obtener("b")).contains(2);
        assertThat(cache.obtener("c")).contains(3);
        assertThat(cache.tamano()).isEqualTo(2);
    }

    // 3. agregarTodos
    @Test
    void agregarTodosAceptaSupertiposEnElDestinoYSubtiposEnElOrigen() {
        List<Number> numeros = new ArrayList<>();
        List<Integer> enteros = List.of(1, 2);
        List<Double> decimales = List.of(0.5);
        assertThat(Colecciones.<Number>agregarTodos(numeros, enteros)).isEqualTo(2);
        assertThat(Colecciones.<Number>agregarTodos(numeros, decimales)).isEqualTo(1);
        List<Object> objetos = new ArrayList<>();
        Colecciones.agregarTodos(objetos, enteros);                    // T = Integer, destino ? super Integer
        Set<Integer> conjunto = new TreeSet<>(List.of(1));
        assertThat(Colecciones.agregarTodos(conjunto, enteros)).isEqualTo(1);   // el 1 ya estaba
        assertThat(numeros).containsExactly(1, 2, 0.5);
    }

    // 4. Invarianza
    @Test
    void listIntegerNoEsListNumber() {
        var errores = Compilador.errores("Invarianza", """
                import java.util.*;
                class Invarianza {
                    void f() {
                        List<Integer> enteros = new ArrayList<>();
                        List<Number> numeros = enteros;
                        numeros.add(3.14);
                    }
                }
                """);
        assertThat(errores).singleElement().asString()
                .contains("incompatible types: java.util.List<java.lang.Integer> cannot be converted to java.util.List<java.lang.Number>");
    }

    @Test
    void conArreglosLaCovarianzaCompilaYFallaEnEjecucion() {
        Integer[] enteros = new Integer[1];
        Number[] numeros = enteros;                      // compila: los arreglos son covariantes
        assertThatThrownBy(() -> numeros[0] = 3.14).isInstanceOf(ArrayStoreException.class);
    }

    // 5. new T[10]
    @Test
    void newTArrayNoCompila() {
        var errores = Compilador.errores("Caja", """
                class Caja<T> {
                    T[] elementos = new T[10];
                }
                """);
        assertThat(errores).singleElement().asString().contains("generic array creation");
    }

    @Test
    void alternativasANewTArray() {
        String[] a = Colecciones.arregloDe(String.class, 3);
        String[] b = Colecciones.arregloCon(String[]::new, 4);
        assertThat(a).hasSize(3).isInstanceOf(String[].class);
        assertThat(b).hasSize(4).isInstanceOf(String[].class);
        List<String> c = Colecciones.listaDe(10);
        c.add("x");
        assertThat(c).containsExactly("x");
    }
}
