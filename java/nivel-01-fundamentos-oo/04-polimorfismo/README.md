# 04 — Polimorfismo

## Paso 1 — Explicación para principiantes

El botón de "reproducir" existe en un televisor, en un parlante y en un celular. Tú presionas el mismo botón y cada aparato hace algo distinto por dentro. No necesitas saber qué aparato es para saber qué botón presionar.

Polimorfismo es eso: enviar el mismo mensaje a objetos distintos y que cada uno responda a su manera, sin que quien envía el mensaje tenga que preguntar "¿qué eres?".

## Paso 2 — Explicación técnica

Polimorfismo de subtipos: una variable de un tipo general puede referirse a objetos de tipos más específicos, y la llamada a un método se resuelve **en tiempo de ejecución** según el tipo real del objeto (despacho dinámico).

- **Tipo estático**: el declarado en la variable. Decide qué métodos se pueden llamar.
- **Tipo dinámico**: el del objeto real. Decide qué implementación se ejecuta.

```java
Canal canal = new CanalSms("3001234567");   // estático: Canal; dinámico: CanalSms
canal.enviar("hola");                       // se ejecuta CanalSms.enviar
```

### Sobrescritura y sobrecarga no son lo mismo

| | Sobrescritura (override) | Sobrecarga (overload) |
|---|---|---|
| Qué es | Una subclase redefine un método heredado | Varios métodos con el mismo nombre y distintos parámetros |
| Cuándo se resuelve | En ejecución, por el tipo dinámico | En compilación, por el tipo estático |
| ¿Es polimorfismo de subtipos? | Sí | No |

### La señal de que falta polimorfismo

Una cadena de `if`/`switch` que pregunta por el tipo (`instanceof`, un campo `tipo`, un texto) para decidir qué hacer. Cada caso nuevo obliga a encontrar y modificar todas esas cadenas.

### Por qué importa más de lo que parece

El polimorfismo permite que el código que **usa** un comportamiento no dependa del código que lo **implementa**. Quien llama solo conoce el contrato. Esa es la base de OCP, de DIP y de casi todos los patrones de diseño.

## Paso 3 — Código que funciona, pero está mal diseñado

<!-- file: ejemplos/antes/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        notificar("CORREO", "ana@correo.example", "Tu pedido fue enviado");
        notificar("SMS", "3001234567", "Tu pedido fue enviado");
        System.out.println("Costo SMS: " + costo("SMS"));
        System.out.println("Costo PUSH: " + costo("PUSH"));
    }

    static void notificar(String canal, String destino, String mensaje) {
        if (canal.equals("CORREO")) {
            System.out.println("[correo a " + destino + "] " + mensaje);
        } else if (canal.equals("SMS")) {
            String texto = mensaje.length() > 20 ? mensaje.substring(0, 20) : mensaje;
            System.out.println("[sms a " + destino + "] " + texto);
        } else if (canal.equals("PUSH")) {
            System.out.println("[push a " + destino + "] " + mensaje);
        }
    }

    static long costo(String canal) {
        if (canal.equals("CORREO")) {
            return 0;
        } else if (canal.equals("SMS")) {
            return 50;
        }
        return 0; // ¿PUSH es gratis o alguien olvidó agregarlo?
    }
}
```

## Paso 4 — Análisis

- **El conocimiento de cada canal está repartido** entre `notificar` y `costo`. Para entender "qué es un SMS" hay que leer dos métodos; en un sistema real, veinte.
- **Agregar WhatsApp obliga a modificar cada cadena de `if`.** Olvidar una no da error de compilación: da un comportamiento por defecto silencioso, como en `costo("PUSH")`.
- **Un texto mal escrito** (`"Sms"`) no falla: simplemente no notifica.
- **No se puede probar un canal sin pasar por el método que los conoce todos.**

## Paso 5 — Código mejorado

<!-- file: ejemplos/despues/Main.java -->
```java
import java.util.List;

public class Main {

    public static void main(String[] args) {
        List<Canal> canales = List.of(
                new CanalCorreo("ana@correo.example"),
                new CanalSms("3001234567"),
                new CanalPush("dispositivo-77"));

        long costoTotal = 0;
        for (Canal canal : canales) {
            canal.enviar("Tu pedido fue enviado");
            costoTotal += canal.costo();
        }
        System.out.println("Costo total: " + costoTotal);

        // Sobrecarga: se decide al compilar, por el tipo de la variable.
        Object texto = "hola";
        imprimir(texto);
        imprimir("hola");
    }

    static void imprimir(Object valor) {
        System.out.println("versión Object: " + valor);
    }

    static void imprimir(String valor) {
        System.out.println("versión String: " + valor);
    }
}

interface Canal {
    void enviar(String mensaje);
    long costo();
}

class CanalCorreo implements Canal {

    private final String direccion;

    CanalCorreo(String direccion) {
        this.direccion = direccion;
    }

    @Override
    public void enviar(String mensaje) {
        System.out.println("[correo a " + direccion + "] " + mensaje);
    }

    @Override
    public long costo() {
        return 0;
    }
}

class CanalSms implements Canal {

    private static final int LONGITUD_MAXIMA = 20;
    private final String celular;

    CanalSms(String celular) {
        this.celular = celular;
    }

    @Override
    public void enviar(String mensaje) {
        String texto = mensaje.length() > LONGITUD_MAXIMA
                ? mensaje.substring(0, LONGITUD_MAXIMA) : mensaje;
        System.out.println("[sms a " + celular + "] " + texto);
    }

    @Override
    public long costo() {
        return 50;
    }
}

class CanalPush implements Canal {

    private final String dispositivo;

    CanalPush(String dispositivo) {
        this.dispositivo = dispositivo;
    }

    @Override
    public void enviar(String mensaje) {
        System.out.println("[push a " + dispositivo + "] " + mensaje);
    }

    @Override
    public long costo() {
        return 0;
    }
}
```

Qué cambió:

1. **Todo lo que es un SMS está en `CanalSms`.** Límite de caracteres, costo y forma de envío, juntos.
2. **El bucle no pregunta qué tipo es cada canal.** Envía el mismo mensaje a todos.
3. **Agregar WhatsApp es crear una clase.** Y el compilador obliga a implementar `costo()`: ya no existe el olvido silencioso.
4. **El final de `main` muestra la trampa de la sobrecarga:** `imprimir(texto)` ejecuta la versión `Object` aunque el objeto sea un `String`, porque la sobrecarga se decide con el tipo de la variable.

Costo: de un archivo con dos métodos a una interfaz y tres clases. Si solo existiera un canal y nada indicara que habrá más, el `if` no existiría y la interfaz tampoco haría falta.

## Paso 6 — Taller práctico

<!-- file: taller/inicio/Main.java -->
```java
public class Main {

    public static void main(String[] args) {
        System.out.println(costoEnvio("MOTO", 3, 8));
        System.out.println(costoEnvio("CAMION", 300, 120));
        System.out.println(tiempoEstimadoHoras("MOTO", 8));
        System.out.println(puedeLlevar("MOTO", 40));
    }

    static long costoEnvio(String transporte, int pesoKg, int distanciaKm) {
        switch (transporte) {
            case "MOTO": return 3_000 + distanciaKm * 500L;
            case "CAMION": return 50_000 + distanciaKm * 1_200L + pesoKg * 100L;
            case "BICICLETA": return 2_000 + distanciaKm * 300L;
            default: return 0;
        }
    }

    static int tiempoEstimadoHoras(String transporte, int distanciaKm) {
        switch (transporte) {
            case "MOTO": return Math.max(1, distanciaKm / 30);
            case "CAMION": return Math.max(2, distanciaKm / 50);
            case "BICICLETA": return Math.max(1, distanciaKm / 12);
            default: return 0;
        }
    }

    static boolean puedeLlevar(String transporte, int pesoKg) {
        switch (transporte) {
            case "MOTO": return pesoKg <= 20;
            case "CAMION": return pesoKg <= 5_000;
            default: return false; // la bicicleta quedó sin caso
        }
    }
}
```

1. Reemplaza los `switch` por polimorfismo.
2. Agrega un transporte nuevo (dron: hasta 2 kg, hasta 10 km) y lista qué archivos existentes tuviste que modificar.
3. Escribe un método que, dada una lista de transportes y un envío, devuelva el más barato que pueda llevarlo.
4. En algún lugar del programa hay que convertir un texto (`"MOTO"`) en un objeto. ¿Dónde pusiste ese `switch` y por qué ese sí es aceptable?
5. Escribe un ejemplo propio donde la sobrecarga produzca un resultado que sorprenda.

Entrega: rama `taller/n1-04-polimorfismo`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Paso 7 — Revisión técnica

- ¿Queda algún `if`/`switch` por tipo fuera del punto de creación?
- ¿El código cliente conoce alguna clase concreta?
- ¿El comportamiento por defecto silencioso desapareció?
- ¿El contrato tiene solo lo que todos los tipos pueden cumplir?

## Paso 8 — Aplicación profesional

- **Spring inyecta todas las implementaciones de una interfaz** si pides `List<Canal>` o `Map<String, Canal>` en el constructor. Un servicio puede recorrerlas o elegir una por nombre sin un solo `if`. Agregar un canal es agregar un `@Component`.
- **`@Qualifier` y `@Primary`** resuelven cuál implementación entregar cuando hay varias y se necesita una.
- **Los `switch` por tipo de documento, tipo de pago o tipo de cliente** repartidos por los servicios son la forma más común de este problema en código empresarial.
- **No todo `switch` es malo.** Un `switch` sobre un `enum` en un único lugar, o el *pattern matching* sobre una jerarquía `sealed`, son herramientas válidas cuando los casos son pocos, estables y el compilador verifica que estén todos.

## Paso 9 — Evaluación senior

1. ¿Por qué decimos que el polimorfismo invierte dependencias? Dibuja quién depende de quién antes y después.
2. Tienes un `switch` por tipo en un solo método y los tipos no han cambiado en tres años. ¿Lo refactorizas? Justifica.
3. ¿Qué diferencia práctica hay entre un `switch` sobre texto, sobre `enum` y sobre una interfaz `sealed`?
4. ¿Qué problema aparece cuando un método del contrato no tiene sentido para una de las implementaciones?
5. ¿Cómo eliges la implementación correcta en tiempo de ejecución sin volver a escribir la cadena de `if`?

## Comprobación antes de avanzar

- Tipo estático frente a tipo dinámico.
- Sobrescritura frente a sobrecarga y cuándo se resuelve cada una.
- Cómo reconocer un `if` por tipo y qué cuesta.
- Por qué el polimorfismo desacopla a quien usa de quien implementa.

Siguiente: [05 — Abstracción, interfaces y clases abstractas](../05-abstraccion-interfaces-clases-abstractas/).
