# 03 — TDD sobre código heredado

## Idea

Restaurar una casa vieja. Antes de tumbar una pared, el restaurador fotografía todo, apuntala y averigua qué sostiene esa pared. No porque la casa esté bien hecha, sino porque no sabe qué partes de ese desorden están cargando el techo. El código heredado sin pruebas es esa casa: lo primero no es mejorarlo, es apuntalarlo.

## Lo esencial

**Código heredado** es código sin pruebas. Da igual su edad. Sin pruebas, cada cambio es una apuesta.

### El dilema

Para cambiar el código con seguridad hacen falta pruebas. Para poder ponerle pruebas, casi siempre hay que cambiarlo. Se sale de ahí con cambios muy pequeños y mecánicos, de bajo riesgo, hechos solo para poder probar.

### Pruebas de caracterización

No comprueban lo que el código **debería** hacer, sino lo que **hace hoy**, errores incluidos. Se escriben así:

1. Llama al código con una entrada.
2. Mira qué devuelve.
3. Escribe una prueba que espere exactamente eso.

Cuando hay muchas combinaciones, se generan todas, se guarda la salida del código actual (una **muestra de referencia**) y se compara contra ella después de cada cambio.

Un comportamiento raro que encuentres **no se corrige** en este paso. Puede ser un error, o una regla de negocio que alguien pidió hace seis años. Se anota, se pregunta, y se corrige después, como un cambio aparte y con su propia prueba.

### Costuras

Una **costura** es un punto donde puedes cambiar el comportamiento sin editar el código en ese sitio. Sirven para separar la lógica de lo que la hace imposible de probar.

| Obstáculo | Costura habitual |
|---|---|
| Usa la hora del sistema | Pasar la hora como parámetro o inyectar un reloj |
| Estado global o estático | Envolverlo en un objeto que se pueda sustituir |
| Imprime, escribe archivos o llama a la red en mitad del cálculo | Extraer el cálculo a una función que solo devuelve un valor |
| Un método de trescientas líneas | Extraer el trozo que vas a cambiar y probar solo ese |
| Crea sus dependencias con `new` | Recibirlas por el constructor |

### El orden

1. Identificar qué vas a cambiar.
2. Encontrar una costura.
3. Romper la dependencia con el cambio más pequeño posible.
4. Escribir pruebas de caracterización.
5. Refactorizar con las pruebas en verde.
6. **Ahora sí:** añadir el comportamiento nuevo con el ciclo de TDD.

## Ejemplo

Una función de costo de envío que nadie se atreve a tocar.

### Antes

El enfoque habitual: "esto es un desastre, lo reescribo limpio". Se reescribe leyendo el código y entendiéndolo "más o menos". Se prueba con tres casos. En producción aparece que los clientes VIP con envío urgente local pagan mil pesos más que antes, y nadie sabe si eso es un error nuevo o uno viejo que se arregló sin querer.

### Análisis

| Problema | Consecuencia |
|---|---|
| Se reescribió sin saber qué hacía exactamente | Se cambió comportamiento sin querer y sin enterarse |
| No había forma de comparar antes y después | La única prueba fue producción |
| Limpiar y cambiar comportamiento se mezclaron | Cuando algo falla, no se sabe cuál de las dos cosas lo causó |

### Después

Se genera la muestra de referencia con el código actual sobre todas las combinaciones, se refactoriza y se compara. Ejecútalo con `java Main.java`:

<!-- file: ejemplo/Main.java -->
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Caracterizar antes de tocar: el código viejo es la referencia del nuevo. */
public class Main {

    // ----- El código heredado, tal cual. No se toca. -----
    static double envioHeredado(double peso, String zona, boolean urgente, String tipoCliente) {
        double r = 0;
        if (zona.equals("LOCAL")) {
            if (peso <= 1) r = 5000; else if (peso <= 5) r = 8000; else r = 8000 + (peso - 5) * 1200;
        } else if (zona.equals("NACIONAL")) {
            if (peso <= 1) r = 9000; else if (peso <= 5) r = 14000; else r = 14000 + (peso - 5) * 2000;
        } else {
            r = 30000 + peso * 4000;
        }
        if (urgente) {
            if (zona.equals("LOCAL")) r = r * 1.5; else r = r * 1.8;
        }
        if (tipoCliente.equals("VIP")) {
            r = r - r * 0.1;
            if (urgente && zona.equals("LOCAL")) r = r - 1000;
        } else if (tipoCliente.equals("EMPLEADO")) {
            r = r / 2;
        }
        if (r < 0) r = 0;
        return Math.round(r);
    }

    // ----- La versión refactorizada: mismas reglas, con nombre -----
    static double envio(double peso, String zona, boolean urgente, String tipoCliente) {
        double base = tarifaBase(peso, zona);
        double conUrgencia = urgente ? base * recargoPorUrgencia(zona) : base;
        double conDescuento = aplicarDescuento(conUrgencia, zona, urgente, tipoCliente);
        return Math.round(Math.max(0, conDescuento));
    }

    static double tarifaBase(double peso, String zona) {
        return switch (zona) {
            case "LOCAL" -> porTramos(peso, 5000, 8000, 1200);
            case "NACIONAL" -> porTramos(peso, 9000, 14000, 2000);
            default -> 30000 + peso * 4000;
        };
    }

    static double porTramos(double peso, double hastaUnKilo, double hastaCincoKilos, double porKiloAdicional) {
        if (peso <= 1) {
            return hastaUnKilo;
        }
        if (peso <= 5) {
            return hastaCincoKilos;
        }
        return hastaCincoKilos + (peso - 5) * porKiloAdicional;
    }

    static double recargoPorUrgencia(String zona) {
        return zona.equals("LOCAL") ? 1.5 : 1.8;
    }

    static double aplicarDescuento(double valor, String zona, boolean urgente, String tipoCliente) {
        return switch (tipoCliente) {
            // La rebaja extra de 1000 al VIP urgente local es rara. Se conserva: no sabemos si alguien la pidió.
            case "VIP" -> valor - valor * 0.1 - (urgente && zona.equals("LOCAL") ? 1000 : 0);
            case "EMPLEADO" -> valor / 2;
            default -> valor;
        };
    }

    public static void main(String[] args) {
        List<Double> pesos = List.of(0.5, 1.0, 1.01, 3.0, 5.0, 5.01, 10.0, 25.0);      // a cada lado de cada límite
        List<String> zonas = List.of("LOCAL", "NACIONAL", "INTERNACIONAL");
        List<Boolean> urgencias = List.of(false, true);
        List<String> clientes = List.of("NORMAL", "VIP", "EMPLEADO");

        // 1. La muestra de referencia: lo que el código heredado hace HOY
        Map<String, Double> referencia = new LinkedHashMap<>();
        for (double peso : pesos) {
            for (String zona : zonas) {
                for (boolean urgente : urgencias) {
                    for (String cliente : clientes) {
                        String caso = peso + " kg, " + zona + (urgente ? ", urgente, " : ", normal, ") + cliente;
                        referencia.put(caso, envioHeredado(peso, zona, urgente, cliente));
                    }
                }
            }
        }

        // 2. La versión nueva tiene que dar exactamente lo mismo en todos los casos
        int diferencias = 0;
        for (double peso : pesos) {
            for (String zona : zonas) {
                for (boolean urgente : urgencias) {
                    for (String cliente : clientes) {
                        String caso = peso + " kg, " + zona + (urgente ? ", urgente, " : ", normal, ") + cliente;
                        double esperado = referencia.get(caso);
                        double obtenido = envio(peso, zona, urgente, cliente);
                        if (esperado != obtenido) {
                            diferencias++;
                            System.out.println("DIFERENCIA en " + caso + ": antes " + esperado + ", ahora " + obtenido);
                        }
                    }
                }
            }
        }

        System.out.println(referencia.size() + " combinaciones comparadas, " + diferencias + " diferencias.");
        System.out.println("Ejemplo: 3.0 kg, LOCAL, urgente, VIP = " + referencia.get("3.0 kg, LOCAL, urgente, VIP"));
        if (diferencias > 0) {
            System.exit(1);
        }
    }
}
```

- El código viejo no se tocó: sirvió de referencia.
- Las combinaciones incluyen valores a cada lado de cada límite, que es donde se esconden los errores.
- La regla rara del VIP se conservó, con un comentario. Preguntar por ella es el paso siguiente, no este.
- Con las 144 combinaciones en verde, ahora sí se puede añadir una regla nueva con TDD sobre un código que se entiende.

## Errores comunes

- Reescribir desde cero en lugar de caracterizar.
- "Corregir" durante la caracterización un comportamiento que parecía un error.
- Limpiar y cambiar comportamiento en el mismo commit.
- Refactorizar a lo grande para "hacerlo probable" antes de tener una sola prueba.
- Caracterizar solo el camino feliz.
- Dejar las pruebas de caracterización para siempre, tal cual, en lugar de sustituirlas poco a poco por pruebas que expresen la intención.
- Proponerse dejar todo el sistema con pruebas: se pone bajo prueba lo que se va a tocar.

## Taller

**Objetivo:** poner bajo prueba un código heredado real, refactorizarlo sin cambiar lo que hace y después añadirle una regla nueva con TDD.

Código de inicio: [`taller/inicio/Parqueadero.java`](taller/inicio/Parqueadero.java). Calcula el cobro de un parqueadero. Lleva años en producción, no tiene pruebas y nadie recuerda todas sus reglas. Compila y corre con `java Parqueadero.java`.

1. Sin modificarlo, intenta escribir una prueba para `salir`. Anota cada obstáculo que encuentres.
2. Lee el código y haz una lista de las reglas que **crees** que tiene. Guárdala: la compararás al final.
3. Encuentra las costuras. Para cada obstáculo, elige el cambio más pequeño que te permita probar. Hazlos de uno en uno, cada uno en su commit, sin cambiar comportamiento.
4. Genera una muestra de referencia: combinaciones de tipo de vehículo, minutos (a cada lado de cada límite que veas en el código), día de la semana, hora de salida y sello. Guárdala en un archivo.
5. Escribe la prueba que compara el código contra la muestra.
6. ¿Encontraste comportamientos sospechosos? Lístalos con el caso exacto que los muestra. **No los corrijas.** Para cada uno, escribe la pregunta que le harías al negocio.
7. Refactoriza hasta que cada regla tenga nombre y se lea. La muestra debe pasar después de cada commit.
8. Compara las reglas que encontraste con tu lista del paso 2. ¿Cuántas te faltaban o tenías mal?
9. Ahora, con TDD: añade la regla "los vehículos eléctricos pagan la mitad". Rojo, verde, refactor, con prefijos en los commits.
10. El negocio responde que uno de tus hallazgos del paso 6 sí era un error. Corrígelo con TDD y actualiza la muestra **a propósito**, explicando en el commit qué casos cambian y por qué.
11. Sustituye tres casos de la muestra por pruebas con nombre que expresen una regla de negocio.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Hay un commit de caracterización anterior a cualquier refactor | El historial |
| Ningún commit mezcla refactor y cambio de comportamiento | El historial y los mensajes |
| La muestra cubre los límites | Lectura de las combinaciones |
| Las pruebas no dependen de la hora real | Se ejecutan igual a cualquier hora |
| Los comportamientos sospechosos están documentados con su caso | `ANALISIS.md` |

**Entrega:** rama `taller/tdd-03`, con el código en `taller/solucion/` y `ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

La mayor parte del trabajo de un desarrollador es sobre código que ya existe. Saber hacer TDD en un proyecto nuevo es útil; saber meter pruebas en un método de quinientas líneas sin romper nada es lo que te hace valioso en un equipo con un sistema de diez años.

## Preguntas senior

1. ¿Qué es una prueba de caracterización y en qué se diferencia de una prueba normal?
2. ¿Por qué no se corrigen los errores que aparecen al caracterizar?
3. ¿Qué es una costura? Da tres ejemplos.
4. ¿Reescribir o refactorizar? ¿De qué depende?
5. Tienes dos días para cambiar una regla en un módulo sin pruebas. ¿Qué haces y qué no?
