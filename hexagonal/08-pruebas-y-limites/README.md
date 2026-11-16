# 08 — Pruebas por capa, regla de dependencias y límites

## Idea

Un edificio con instalaciones bien separadas. Para probar la bomba de agua no hace falta encender los ascensores, y para cambiar el cableado no hay que picar las tuberías. Pero eso tiene un precio: planos, ductos y registros que una caseta de jardín no necesita. Nadie pone cuarto de máquinas en una caseta.

Esta sesión cierra el módulo con las dos caras: lo que la separación permite probar, y cuándo no merece la pena pagarla.

## Lo esencial

### Qué se prueba en cada capa

| Capa | Qué se prueba | Con qué | Velocidad | Sesión |
|---|---|---|---|---|
| Dominio | Cada regla y cada borde | Objetos, nada más | Milisegundos | 02 |
| Aplicación | La coordinación: qué se guarda, qué se rechaza, qué se publica | El servicio real con adaptadores de memoria | Milisegundos | 03 a 05 |
| Adaptador de entrada | La traducción: formato, códigos, errores | Peticiones reales contra el núcleo con memoria | Rápida | 06 |
| Adaptador de salida | Que cumple el contrato del puerto | La tecnología real (una base de datos de verdad) | Lenta | Esta |
| Arquitectura | Que las dependencias apuntan hacia dentro | Una prueba que lee los `import` | Rápida | Esta |
| Sistema completo | Que todo está bien conectado | Pocas pruebas de extremo a extremo | La más lenta | — |

La mayoría de las pruebas vive arriba de la tabla, donde son baratas. Es la consecuencia práctica de haber sacado las reglas al dominio.

### El contrato de un puerto

Un puerto es una promesa. Si tienes dos adaptadores para el mismo puerto, los dos deben cumplirla **igual**; si no, las pruebas con memoria no dicen nada sobre producción.

La forma de asegurarlo es escribir las pruebas del puerto **una vez** y ejecutarlas contra cada adaptador.

| Cláusula del contrato de `RepositorioDeCuentas` | Por qué importa |
|---|---|
| Lo guardado se recupera igual | Lo básico |
| Buscar algo que no existe devuelve vacío, no falla | El caso de uso decide qué hacer |
| Lo que se entrega es una copia | Modificar sin guardar no debe cambiar lo guardado |
| Guardar con una versión vieja falla con conflicto | Es la base del bloqueo optimista |
| Los identificadores no se repiten | — |

### La regla de dependencias, automatizada

Las convenciones que nadie comprueba se rompen un viernes por la tarde. La regla "el dominio no importa nada de fuera" se puede verificar con una prueba que lea los `import` de cada archivo.

| Regla | Qué protege |
|---|---|
| El dominio solo importa dominio y JDK | Que las reglas no dependan de ninguna tecnología |
| La aplicación solo importa dominio, aplicación y JDK | Que los casos de uso no conozcan adaptadores |
| Los adaptadores de entrada no importan los de salida, ni al revés | Que nadie se salte la aplicación |
| Los adaptadores de entrada solo usan puertos de entrada | Que no manipulen repositorios directamente |

### Lo que cuesta

| Coste | En qué se nota |
|---|---|
| Más archivos | Un puerto, un adaptador y un modelo por cada cosa |
| Traducción entre modelos | Código repetitivo entre API, dominio y persistencia |
| Indirección | Para seguir una petición hay que saltar por interfaces |
| Curva de entrada | Quien llega nuevo tarda en saber dónde va cada cosa |
| Riesgo de ceremonia | Puertos que solo tienen una implementación y nunca tendrán otra |

### Cuándo sí y cuándo no

| Sí | No |
|---|---|
| Hay reglas de negocio que cambian y hay que probar | Es un CRUD: guardar y leer sin reglas |
| El sistema va a durar años | Es un prototipo o un script |
| Hay varias entradas (API, mensajes, tareas) para las mismas operaciones | Hay una sola entrada y no se espera otra |
| Las integraciones externas son inestables o cambian | No hay integraciones |
| El equipo necesita probar sin levantar infraestructura | El equipo es una persona y el alcance, una semana |

No es todo o nada. En un mismo sistema puede haber un módulo con reglas, hecho así, y otro que es un CRUD de configuración, hecho de la forma más simple posible.

### Señales de exceso

- Puertos de salida para cosas que nunca van a cambiar ni hace falta simular.
- Un caso de uso que solo llama a un método del repositorio y devuelve lo mismo.
- Cuatro modelos casi idénticos y tres traducciones para un dato que pasa sin cambios.
- Un dominio sin comportamiento: clases con datos y servicios con toda la lógica. Eso no es hexagonal, es un CRUD con más archivos.

### Nombres parecidos

Hexagonal, *clean architecture* y *onion architecture* comparten la idea central: las reglas en el centro y las dependencias hacia dentro. Cambian los nombres de las capas y el nivel de detalle. Si entiendes una, lees las otras.

## El código

### El contrato del repositorio

<!-- file: ../proyecto/pruebas/banco/ContratoDelRepositorio.java -->
```java
package banco;

import banco.aplicacion.ConflictoDeConcurrencia;
import banco.aplicacion.puertos.salida.RepositorioDeCuentas;
import banco.dominio.Cuenta;
import banco.dominio.Dinero;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Lo que cualquier adaptador de RepositorioDeCuentas debe cumplir. Se escribe una vez y se ejecuta contra cada uno. */
public final class ContratoDelRepositorio {

    private static final LocalDate HOY = LocalDate.parse("2026-11-15");

    private ContratoDelRepositorio() {
    }

    /** Devuelve las cláusulas que el adaptador incumple. Vacío significa que cumple el contrato. */
    public static List<String> incumplimientos(Supplier<RepositorioDeCuentas> fabrica) {
        List<String> incumplidas = new ArrayList<>();

        RepositorioDeCuentas cuentas = fabrica.get();
        String id = cuentas.siguienteId();
        cuentas.guardar(Cuenta.abrir(id, "Ana", Dinero.pesos(1_000), HOY));

        Cuenta leida = cuentas.buscar(id).orElse(null);
        if (leida == null || !leida.titular().equals("Ana") || leida.saldo().centavos() != 100_000) {
            incumplidas.add("lo guardado se recupera igual");
            return incumplidas;
        }
        if (cuentas.buscar("no-existe").isPresent()) {
            incumplidas.add("buscar algo que no existe devuelve vacío");
        }
        if (cuentas.siguienteId().equals(id)) {
            incumplidas.add("los identificadores no se repiten");
        }

        leida.depositar(Dinero.pesos(500));                              // se modifica y NO se guarda
        if (cuentas.buscar(id).orElseThrow().saldo().centavos() != 100_000) {
            incumplidas.add("lo que se entrega es una copia");
        }

        Cuenta unaLectura = cuentas.buscar(id).orElseThrow();
        Cuenta otraLectura = cuentas.buscar(id).orElseThrow();
        unaLectura.depositar(Dinero.pesos(1));
        cuentas.guardar(unaLectura);
        otraLectura.depositar(Dinero.pesos(2));
        try {
            cuentas.guardar(otraLectura);                                // ya es vieja
            incumplidas.add("guardar con una versión vieja falla con conflicto");
        } catch (ConflictoDeConcurrencia esperado) {
            // cumple
        }
        return incumplidas;
    }
}
```

Ese mismo contrato es el que debe pasar `AlmacenJpa` de la sesión 07, ejecutado contra un PostgreSQL real. Si lo pasan los dos, las pruebas con memoria hablan también de producción. Comprobado con Testcontainers y PostgreSQL 17: lo pasa entero. Un detalle: `AlmacenJpa` necesita una transacción abierta para escribir, así que en esa prueba cada llamada del contrato va en la suya, que es lo que `AlmacenEnMemoria` hace solo cuando no hay ninguna abierta.

### La regla de dependencias

<!-- file: ../proyecto/pruebas/banco/PruebasDeArquitectura.java -->
```java
package banco;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Lee los import de cada archivo y comprueba que las dependencias apuntan hacia dentro. */
public final class PruebasDeArquitectura {

    private PruebasDeArquitectura() {
    }

    public static void ejecutar() {
        System.out.println("Arquitectura:");
        Path fuentes = Path.of(System.getProperty("fuentes", "src"));
        if (!Files.isDirectory(fuentes.resolve("banco"))) {
            Comprobar.igual("se encuentran las fuentes (ejecuta desde proyecto/ o indica -Dfuentes=ruta)", true, false);
            return;
        }
        Comprobar.igual("el dominio solo depende de sí mismo y del JDK", List.of(),
                importsQue(fuentes, "banco/dominio", fuera("java.", "banco.dominio.")));
        Comprobar.igual("la aplicación solo depende del dominio, de sí misma y del JDK", List.of(),
                importsQue(fuentes, "banco/aplicacion", fuera("java.", "banco.dominio.", "banco.aplicacion.")));
        Comprobar.igual("los adaptadores de entrada no conocen a los de salida", List.of(),
                importsQue(fuentes, "banco/adaptadores/entrada", dentro("banco.adaptadores.salida.")));
        Comprobar.igual("los adaptadores de salida no conocen a los de entrada", List.of(),
                importsQue(fuentes, "banco/adaptadores/salida", dentro("banco.adaptadores.entrada.")));
        Comprobar.igual("los adaptadores de entrada solo usan puertos de entrada", List.of(),
                importsQue(fuentes, "banco/adaptadores/entrada", dentro("banco.aplicacion.puertos.salida.", "banco.aplicacion.ServicioDeCuentas")));
    }

    private static Predicate<String> dentro(String... prefijos) {
        return importado -> Arrays.stream(prefijos).anyMatch(importado::startsWith);
    }

    private static Predicate<String> fuera(String... prefijos) {
        return dentro(prefijos).negate();
    }

    /** Los import de los archivos de una carpeta que cumplen la condición, como "Archivo.java -> lo.importado". */
    private static List<String> importsQue(Path fuentes, String carpeta, Predicate<String> prohibido) {
        List<String> encontrados = new ArrayList<>();
        try (Stream<Path> archivos = Files.walk(fuentes.resolve(carpeta))) {
            for (Path archivo : archivos.filter(ruta -> ruta.toString().endsWith(".java")).sorted().toList()) {
                for (String linea : Files.readAllLines(archivo)) {
                    if (linea.startsWith("import ")) {
                        String importado = linea.substring("import ".length()).replace("static ", "").replace(";", "").trim();
                        if (prohibido.test(importado)) {
                            encontrados.add(archivo.getFileName() + " -> " + importado);
                        }
                    }
                }
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return encontrados;
    }
}
```

### Todas juntas

<!-- file: ../proyecto/pruebas/banco/TodasLasPruebas.java -->
```java
package banco;

import banco.adaptadores.salida.memoria.AlmacenEnMemoria;
import java.util.List;

/** Ejecuta todas las pruebas del proyecto. Termina con código 1 si alguna falla. */
public final class TodasLasPruebas {

    private TodasLasPruebas() {
    }

    public static void main(String[] args) {
        PruebasDelDominio.ejecutar();
        PruebasDeLaAplicacion.ejecutar();
        PruebasDeTransacciones.ejecutar();
        PruebasDeConcurrencia.ejecutar();
        PruebasDeAdaptadores.ejecutar();

        System.out.println("Contrato del repositorio:");
        Comprobar.igual("el almacén en memoria lo cumple entero", List.of(),
                ContratoDelRepositorio.incumplimientos(AlmacenEnMemoria::new));
        Comprobar.igual("el repositorio ingenuo incumple justo la cláusula de concurrencia",
                List.of("guardar con una versión vieja falla con conflicto"),
                ContratoDelRepositorio.incumplimientos(RepositorioIngenuo::new));

        PruebasDeArquitectura.ejecutar();

        System.out.println();
        System.out.println(Comprobar.total() + " comprobaciones, " + Comprobar.fallos() + " fallos");
        System.exit(Comprobar.fallos() == 0 ? 0 : 1);
    }
}
```

Desde `hexagonal/proyecto/`:

```bash
javac -Xlint:all -Werror -d salida $(find src pruebas -name '*.java')
java -cp salida banco.TodasLasPruebas
```

### Rómpela a propósito

Añade esta línea a `src/banco/dominio/Cuenta.java` y vuelve a compilar y ejecutar:

```java
import banco.adaptadores.salida.memoria.AlmacenEnMemoria;
```

Compila sin quejarse: para el compilador es un `import` válido. Quien lo detecta es la prueba de arquitectura, que falla y dice qué archivo importó qué. Deshaz el cambio.

## Errores comunes

- Probar las reglas del negocio a través de HTTP y con base de datos porque "así se prueba todo".
- Dobles de prueba para los puertos de salida que no se comportan como el adaptador real, sin contrato que los compare.
- Simular el dominio en las pruebas de la aplicación: se prueba la simulación.
- Una regla de dependencias que solo existe en un documento.
- Aplicar la arquitectura completa a cada tabla de configuración.
- Decidir que "esto es hexagonal" por los nombres de las carpetas, con las reglas en los servicios y el dominio vacío.

## Taller integrador

**Objetivo:** cerrar tu servicio de pedidos con pruebas en todas las capas, la regla de dependencias automatizada y una valoración honesta de lo que costó.

Sobre tu servicio de las sesiones 01 a 07.

1. Escribe el contrato de tu puerto de repositorio de pedidos y el de existencias. Ejecútalos contra tus adaptadores de memoria.
2. Ejecuta los mismos contratos contra tus adaptadores JPA con un PostgreSQL real. Si alguna cláusula falla en uno y no en otro, decide cuál de los dos tiene razón y corrige.
3. Escribe la prueba de arquitectura para tu proyecto. Rómpela a propósito tres veces, una por regla, y comprueba que avisa.
4. Cuenta tus pruebas por capa y mide cuánto tarda cada grupo. Rellena una tabla como la de esta sesión con tus números.
5. Elige una regla del negocio y busca cuántas pruebas la cubren y en qué capa. Si solo se prueba a través de HTTP, baja la prueba al dominio.
6. Añade un requisito nuevo: "un pedido con más de diez unidades de un mismo producto necesita aprobación". Anota qué archivos tocaste, de qué capa, y cuánto tardaste en tener la primera prueba pasando.
7. Revisa tus puertos de salida: ¿alguno tiene una sola implementación y nunca necesitó un doble? Decide si lo conservas y por qué.
8. Tu servicio tiene, además, una tabla de "motivos de cancelación" que solo se lista y se edita. ¿La harías con la misma arquitectura? Escribe la decisión.
9. Escribe una valoración de una página: qué te dio la arquitectura en este servicio, qué te costó, y en qué proyecto tuyo real no la usarías.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Los contratos pasan contra memoria y contra JPA | Ejecución |
| La prueba de arquitectura falla al introducir cada violación | Los tres intentos del paso 3 |
| Cada regla del negocio tiene al menos una prueba en el dominio | El paso 5 |
| La tabla de pruebas por capa tiene números reales | `ANALISIS.md` |
| La valoración nombra costes concretos, no solo ventajas | Lectura |

**Entrega:** rama `taller/hexagonal-08`, con el proyecto en `taller/solucion/` y `ANALISIS.md` con la tabla del paso 4 y las respuestas de los pasos 6 a 9.

### Lo que sigue

Tu servicio de pedidos es ahora la base de tres rutas. En cada una, el taller parte de él y añade adaptadores, sin tocar el dominio:

| Ruta | Qué añade a tu servicio |
|---|---|
| [Kafka y sagas](../../kafka-y-sagas/) | Un adaptador de salida que publica en Kafka, consumidores como adaptadores de entrada y una saga que coordina pedidos, pagos e inventario |
| [HikariCP](../../hikaricp/) | El pool de conexiones que hay debajo de tu adaptador de persistencia: tamaño, tiempos y diagnóstico |
| [Prácticas](../../practicas/) | TDD sobre el dominio, pruebas de API con Karate sobre el adaptador HTTP y observabilidad |

## En el trabajo real

La regla de dependencias se automatiza con ArchUnit, que expresa estas mismas comprobaciones sobre las clases compiladas, o con módulos de construcción separados: si el módulo del dominio no declara la dependencia, el `import` ni compila. Los contratos de los adaptadores de persistencia se ejecutan con Testcontainers en la integración continua.

En una revisión de código, la pregunta útil no es "¿esto es hexagonal?" sino "¿dónde está esta regla y cómo la pruebo sin levantar nada?".

## Preguntas senior

1. ¿Qué se prueba en cada capa y por qué la mayoría de las pruebas está en el dominio?
2. ¿Qué es una prueba de contrato de un puerto y qué problema evita?
3. ¿Cómo garantizas que la regla de dependencias no se rompe con el tiempo?
4. ¿Cuándo no usarías arquitectura hexagonal?
5. ¿Qué señales indican que un proyecto tiene la forma hexagonal pero no el fondo?
6. ¿Cómo convives con un módulo hexagonal y otro CRUD en el mismo sistema?
