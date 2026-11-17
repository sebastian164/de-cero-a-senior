# 01 — Primera prueba de API con Karate

Karate es un marco de código abierto para probar API: las pruebas se escriben en archivos de texto con una sintaxis propia, sin programar el pegamento entre el texto y el código. Esta ruta usa la serie 2 (2026), que requiere Java 21. Los archivos de esta sesión se ejecutaron con Karate 2.1.4 y JDK 21, y la API contra la que corren responde lo que las pruebas esperan.

## Idea

Un inspector de sanidad con su lista. No cocina ni conoce las recetas: entra al restaurante como un cliente, pide, y comprueba lo que le sirven contra lo que la norma dice. Si el cocinero cambia de sartén, al inspector le da igual; si el plato llega frío, lo anota. Una prueba de API es ese inspector: habla con el servicio por la misma puerta que sus clientes y solo le importa lo que sale.

## Lo esencial

### Dónde encaja

| Nivel | Qué prueba | Herramienta típica |
|---|---|---|
| Unitaria | Una función o clase | JUnit |
| De API | El servicio completo por HTTP, desde fuera | **Karate**, REST Assured |
| De interfaz | El sistema a través del navegador | Playwright |

Una prueba de API no sabe en qué lenguaje está hecho el servicio. Sirve igual para una API en Java, en Node o en lo que sea.

### Anatomía de un archivo `.feature`

| Parte | Para qué |
|---|---|
| `Feature:` | El título del conjunto |
| `Background:` | Pasos que se repiten antes de cada escenario |
| `Scenario:` | Una prueba. Independiente de las demás |
| `Given` / `When` / `Then` / `And` / `*` | Prefijos de paso. Para Karate son equivalentes; se eligen para que se lea bien |

### Los pasos que más vas a usar

| Paso | Qué hace |
|---|---|
| `url 'http://…'` | Dirección base |
| `path 'api', 'notas'` | Añade segmentos a la ruta |
| `param q = 'texto'` | Parámetro de consulta |
| `header X-Clave = 'abc'` | Cabecera |
| `request { nombre: 'Ana' }` | Cuerpo de la petición |
| `method get` | Envía la petición |
| `status 200` | Comprueba el código de respuesta |
| `match response == { … }` | Compara la respuesta |
| `def nombre = valor` | Define una variable |
| `print valor` | Escribe en el informe |

Tras `method`, quedan disponibles `response`, `responseStatus`, `responseHeaders` y `responseTime`.

### Marcadores

`match` no exige valores exactos. Para lo que cambia en cada ejecución hay marcadores:

| Marcador | Acepta |
|---|---|
| `'#string'`, `'#number'`, `'#boolean'`, `'#array'`, `'#object'` | Un valor de ese tipo |
| `'#notnull'`, `'#null'`, `'#present'`, `'#notpresent'` | Presencia |
| `'#ignore'` | Cualquier cosa |
| `'#regex [A-Z]{3}'` | Texto que cumple la expresión |
| `'#? _ > 0'` | Un valor que cumple la condición (`_` es el valor) |
| `'##string'` | Opcional: texto, nulo o ausente |

## La API bajo prueba

[Bitácora](../../../docker-kubernetes/app/), la aplicación de los talleres de Docker. Se arranca sin instalar nada más que el JDK:

```bash
cd docker-kubernetes/app/api
CLAVE_API=secreta java Servidor.java
```

## Ejemplo

### Antes

```gherkin
Feature: pruebas

  Scenario: prueba 1
    Given url 'http://localhost:8080/api/info'
    When method get
    Then status 200

  Scenario: prueba 2
    Given url 'http://localhost:8080/api/notas'
    And request 'hola'
    When method post
    * print response

  Scenario: prueba 3
    Given url 'http://localhost:8080/api/notas'
    When method get
    Then status 200
    And match response.notas[0] == 'hola'
```

### Análisis

| Problema | Consecuencia |
|---|---|
| Solo se comprueba el código 200 | La API puede devolver cualquier cosa con un 200 y la prueba pasa |
| La dirección está escrita en cada escenario | No se puede ejecutar contra otro ambiente sin editar el archivo |
| La "prueba 2" no comprueba nada | Con la clave activada, la API responde 401 y la prueba sigue en verde |
| La "prueba 3" depende de que la 2 haya corrido antes, y de que no haya más notas | Falla al ejecutarse sola, en paralelo o por segunda vez |
| Nombres que no dicen qué se prueba | El informe no le sirve a nadie |

### Después

<!-- file: ejemplo/karate-config.js -->
```javascript
// Configuración común. Karate la ejecuta antes de cada escenario y deja el resultado como variables.
function fn() {
  var ambiente = karate.env || 'local';        // se elige con -Dkarate.env=pruebas

  var config = {
    urlBase: 'http://localhost:8080',
    clave: karate.properties['bitacora.clave'] || 'secreta'   // la real llega por parámetro, no vive en el repositorio
  };

  if (ambiente === 'pruebas') {
    config.urlBase = 'https://bitacora.pruebas.example';
  }

  return config;
}
```

<!-- file: ejemplo/bitacora.feature -->
```gherkin
Feature: API de la bitácora

  Background:
    * url urlBase

  Scenario: informa su versión y quién respondió
    Given path 'api', 'info'
    When method get
    Then status 200
    And match response == { mensaje: '#string', version: '#string', host: '#string', visitas: '#number', notas: '#number' }
    And match response.visitas == '#? _ > 0'

  Scenario: guardar una nota exige la clave
    Given path 'api', 'notas'
    And request 'una nota sin clave'
    When method post
    Then status 401
    And match response == { error: '#string' }

  Scenario: una nota guardada aparece en la lista
    * def nota = 'nota de prueba ' + Date.now()

    Given path 'api', 'notas'
    And header X-Clave = clave
    And request nota
    When method post
    Then status 201
    And match response == { guardada: '#(nota)', host: '#string' }

    Given path 'api', 'notas'
    When method get
    Then status 200
    And match response.notas contains nota

  Scenario: una nota vacía se rechaza
    Given path 'api', 'notas'
    And header X-Clave = clave
    And request '   '
    When method post
    Then status 400
```

<!-- file: ejemplo/BitacoraTest.java -->
```java
// En la serie 1 la clase era com.intuit.karate.junit5.Karate; en la serie 2 cambió de paquete.
import io.karatelabs.junit6.Karate;

class BitacoraTest {

    @Karate.Test
    Karate bitacora() {
        return Karate.run("bitacora").relativeTo(getClass());
    }
}
```

- La dirección y la clave vienen de la configuración: el mismo archivo corre en cualquier ambiente.
- Cada escenario comprueba la **forma** de la respuesta, no solo el código.
- El escenario de la nota crea su propio dato, con un texto único, y comprueba que **está** en la lista, sin suponer cuántas hay ni en qué posición.
- Ningún escenario depende de otro.
- Los nombres dicen el comportamiento.

Para ejecutarlo hace falta un proyecto con la dependencia de Karate para JUnit (`io.karatelabs:karate-junit6`), o la herramienta de línea de órdenes que publica el proyecto. Los informes quedan en `target/karate-reports/`.

## Errores comunes

- Comprobar solo el código de estado.
- Escenarios que dependen del orden.
- Comparar con valores exactos lo que cambia en cada ejecución (identificadores, fechas).
- Direcciones y credenciales escritas en los archivos.
- Suponer que la base está vacía.
- Esperas fijas ("espera cinco segundos") en lugar de reintentar hasta que se cumpla una condición.
- Un solo escenario gigante que prueba veinte cosas.

## Taller

**Objetivo:** montar un proyecto de Karate y cubrir la API de Bitácora completa.

1. Crea un proyecto con Karate siguiendo la guía de inicio de su documentación. Anota la versión exacta que usaste.
2. Arranca Bitácora con `CLAVE_API` y haz pasar el ejemplo de esta sesión. Si algo de la sintaxis cambió en tu versión, corrígelo y documenta qué.
3. Lee las [rutas de la API](../../../docker-kubernetes/app/README.md) y escribe una lista de casos **antes** de escribir pruebas: para cada ruta, el caso normal y los de error.
4. Cubre `/salud/vivo` y `/salud/listo`.
5. Cubre `/api/notas`: método no permitido, nota de más de 500 caracteres, clave incorrecta, nota con comillas y tildes.
6. Cubre `/api/carga`: comprueba la forma de la respuesta y que el tiempo de respuesta sea coherente con el parámetro. ¿Qué pasa con `ms=999999`? ¿Y con `ms=abc`? Decide qué debería pasar y prueba lo que pasa.
7. Una ruta que no existe debe responder 404 con un cuerpo con forma conocida.
8. Ejecuta toda la suite dos veces seguidas sin reiniciar la API. Debe pasar las dos.
9. Ejecuta un solo escenario, por nombre o por etiqueta. Debe pasar solo.
10. Arranca la API con `RETARDO_ARRANQUE_MS=10000` y lanza las pruebas de inmediato. ¿Qué pasa? Haz que la suite espere a que la API esté lista, sin una espera fija.
11. Abre el informe HTML. ¿Qué información da de un escenario que falla? Provoca un fallo y mira.
12. ¿Encontraste algún comportamiento de la API que consideres un defecto? Escríbelo como lo reportarías: pasos, resultado esperado y resultado obtenido.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Todas las rutas tienen caso normal y casos de error | La lista del paso 3 frente a los escenarios |
| La suite pasa dos veces seguidas | Paso 8 |
| Cada escenario pasa solo | Paso 9 |
| No hay direcciones ni claves en los `.feature` | Búsqueda en el repositorio |

**Entrega:** rama `taller/karate-01`, con el proyecto en `taller/solucion/` y `ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

Las pruebas de API son las que más valor dan por línea escrita en un servicio: son rápidas, no se rompen con los refactors internos y fallan cuando se rompe lo que un cliente usa. Corren en la integración continua contra un ambiente levantado para eso.

## Preguntas senior

1. ¿Qué prueba una prueba de API que no prueba una unitaria, y al revés?
2. ¿Por qué los escenarios deben ser independientes?
3. ¿Cómo pruebas una respuesta que tiene valores distintos en cada ejecución?
4. ¿Dónde viven las direcciones y las credenciales de una suite?
5. ¿Cuándo elegirías Karate y cuándo una biblioteca dentro de tus pruebas de Java?
