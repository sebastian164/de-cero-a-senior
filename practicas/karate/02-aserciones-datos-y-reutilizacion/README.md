# 02 — Aserciones, datos y reutilización

Los archivos de esta sesión se ejecutaron con Karate 2.1.4 y JDK 21 contra Bitácora.

## Idea

Un formulario bien diseñado se imprime una vez y se rellena mil. Nadie redacta desde cero la solicitud de cada persona. En una suite de pruebas pasa lo mismo: cuando diez escenarios se diferencian en un dato, lo que quieres es un escenario y una tabla, no diez copias que habrá que corregir diez veces.

## Lo esencial

### `match`, más allá de la igualdad

| Forma | Comprueba |
|---|---|
| `match a == b` | Igualdad exacta, con marcadores |
| `match a != b` | Que sea distinto |
| `match a contains b` | Que el objeto tenga esas claves, o la lista esos elementos |
| `match a contains only b` | Los mismos elementos, en cualquier orden |
| `match a contains any b` | Al menos uno |
| `match a !contains b` | Que no lo contenga |
| `match each lista == esquema` | Que **cada** elemento cumpla |
| `match lista == '#[3]'` | Que tenga tres elementos |
| `match lista == '#[] #string'` | Que sea una lista de textos |

Una lista vacía no cumple `'#[] #string'` ni `match each`: en Karate 2.1.4 ambos fallan con `[]`. Si tu API puede devolver una lista vacía, guarda un dato antes de comprobar la forma, o comprueba con `'#[]'`.

### Esquemas reutilizables

La forma de una respuesta se define una vez y se usa en todos los escenarios:

```gherkin
* def esquemaInfo = { mensaje: '#string', version: '#regex \\d+\\.\\d+\\.\\d+|dev', host: '#string', visitas: '#number', notas: '#number' }
* match response == esquemaInfo
```

Cuando la API añade un campo, se toca un lugar.

### Pruebas guiadas por datos

`Scenario Outline` ejecuta el mismo escenario una vez por cada fila de `Examples`. Los datos también pueden venir de un archivo JSON o CSV.

### Reutilizar pasos

| Mecanismo | Para qué |
|---|---|
| `call read('otro.feature')` | Ejecutar otro archivo como una función y usar lo que devuelve |
| `callonce read('…')` | Lo mismo, una sola vez por archivo: preparación costosa, como iniciar sesión |
| `read('datos.json')` | Cargar datos |
| Etiquetas (`@humo`, `@lento`) | Elegir qué se ejecuta |

### Ambientes y secretos

`karate-config.js` decide la configuración según `karate.env`. Los secretos entran por propiedades del sistema o variables de entorno en el momento de ejecutar; nunca se escriben en el repositorio.

## Ejemplo

### Antes

```gherkin
Feature: notas

  Scenario: nota vacía
    Given url 'http://localhost:8080/api/notas'
    And header X-Clave = 'secreta'
    And request ''
    When method post
    Then status 400

  Scenario: nota de espacios
    Given url 'http://localhost:8080/api/notas'
    And header X-Clave = 'secreta'
    And request '    '
    When method post
    Then status 400

  # …seis escenarios más, iguales salvo el dato

  Scenario: info
    Given url 'http://localhost:8080/api/info'
    When method get
    Then status 200
    And match response == { mensaje: 'Hola desde la bitácora', version: 'dev', host: 'mi-portatil', visitas: 14, notas: 3 }
```

### Análisis

| Problema | Consecuencia |
|---|---|
| Ocho escenarios copiados | Un cambio en la ruta o en la cabecera se hace ocho veces, y alguna se olvida |
| La clave escrita en el archivo | Está en el repositorio y es la misma para todos los ambientes |
| La respuesta de `info` se compara con valores exactos | `host` y `visitas` cambian en cada ejecución y en cada máquina: la prueba falla sin que nada esté roto |
| La forma de la respuesta se repite donde se necesite | Cuando la API cambia, hay que buscarla por toda la suite |

### Después

<!-- file: ejemplo/notas-invalidas.feature -->
```gherkin
Feature: validación de notas

  Background:
    * url urlBase
    * def esquemaError = { error: '#string' }

  Scenario Outline: se rechaza <caso>
    Given path 'api', 'notas'
    And header X-Clave = clave
    And request <cuerpo>
    When method post
    Then status <estado>
    And match response == esquemaError

    Examples:
      | caso                        | cuerpo                    | estado |
      | una nota vacía              | ''                        | 400    |
      | una nota de solo espacios   | '     '                   | 400    |
      | una nota de 501 caracteres  | 'x'.repeat(501)           | 400    |

  Scenario Outline: se rechaza el método <metodo>
    Given path 'api', 'notas'
    And header X-Clave = clave
    When method <metodo>
    Then status 405
    And match response == esquemaError

    Examples:
      | metodo |
      | put    |
      | delete |
```

<!-- file: ejemplo/guardar-nota.feature -->
```gherkin
@ignore
Feature: guardar una nota (para llamar desde otros archivos)

  Scenario:
    Given url urlBase
    And path 'api', 'notas'
    And header X-Clave = clave
    And request texto
    When method post
    Then status 201
```

<!-- file: ejemplo/listado.feature -->
```gherkin
Feature: listado de notas

  Background:
    * url urlBase
    * def sufijo = ' ' + Date.now()

  Scenario: el listado contiene todas las notas guardadas, y todas son texto
    * def primera = 'comprar pan' + sufijo
    * def segunda = 'llamar a mamá' + sufijo
    * call read('guardar-nota.feature') { texto: '#(primera)' }
    * call read('guardar-nota.feature') { texto: '#(segunda)' }

    Given path 'api', 'notas'
    When method get
    Then status 200
    And match response == { host: '#string', notas: '#[] #string' }
    And match response.notas contains [ '#(primera)', '#(segunda)' ]
    And match each response.notas == '#? _.length <= 500'
```

- Los casos de validación son una tabla. Añadir uno es añadir una fila.
- La forma del error se define una vez.
- Guardar una nota es un archivo reutilizable, marcado para que no se ejecute como prueba por sí solo.
- El listado comprueba lo que **debe** cumplirse siempre (todas son texto, ninguna pasa de 500 caracteres, las mías están) y no lo que varía.

## Errores comunes

- Copiar y pegar escenarios.
- `match ==` con valores exactos donde hacía falta un marcador o `contains`.
- Esquemas tan permisivos (`'#ignore'` por todas partes) que no comprueban nada.
- Un archivo "común" enorme del que depende todo: cambiarlo rompe la suite entera.
- `callonce` para algo que modifica datos: los escenarios dejan de ser independientes.
- Tablas de ejemplos con cuarenta columnas.
- Secretos en `karate-config.js`.

## Taller

**Objetivo:** convertir tu suite de la sesión 01 en una suite mantenible, guiada por datos y ejecutable en dos ambientes.

1. Identifica los escenarios de tu suite que solo se diferencian en datos y conviértelos en `Scenario Outline`.
2. Define un esquema por cada respuesta de la API y úsalo en todos los escenarios. Para probarlo, cambia el nombre de un campo en `Servidor.java`: ¿cuántos escenarios fallan y en cuántos sitios tuviste que corregir?
3. Lleva los datos de un `Scenario Outline` a un archivo JSON o CSV.
4. Extrae "guardar una nota" a un archivo reutilizable y úsalo desde tres escenarios.
5. La clave de la API no debe estar en ningún archivo del repositorio. Hazla llegar por parámetro y demuestra que la suite falla con un mensaje claro si no se pasa.
6. Define dos ambientes: `local` y `contenedor` (Bitácora corriendo con Compose, en otro puerto y con otra clave). La misma suite debe pasar en los dos cambiando solo `karate.env`.
7. Etiqueta los escenarios: `@humo` para los cinco imprescindibles y `@lento` para los que usan `/api/carga`. Ejecuta solo los de humo. ¿Cuánto tardan?
8. Escribe una comprobación sobre todas las notas con `match each`. Guarda una nota que la incumpla, si la API te deja, y mira el mensaje de fallo.
9. Prueba un caso con caracteres especiales (comillas, barras invertidas, emojis, saltos de línea) guiado por datos. ¿La API devuelve siempre JSON válido?
10. Revisa tus esquemas: ¿alguno es tan permisivo que pasaría con una respuesta rota? Endurécelo.

**Criterios de aceptación**

- No quedan escenarios duplicados que se diferencien solo en datos.
- Cada forma de respuesta está definida una sola vez.
- La suite pasa en los dos ambientes sin editar ningún `.feature`.
- No hay secretos en el repositorio.

**Entrega:** rama `taller/karate-02`, con el proyecto en `taller/solucion/` y `ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

Una suite de API crece más rápido que el código que prueba. La diferencia entre una que el equipo mantiene y una que termina desactivada está en esto: esquemas en un lugar, datos fuera de los escenarios y nada que dependa del orden.

## Preguntas senior

1. ¿Cuándo usas `==`, `contains` y `match each`?
2. ¿Qué ganas definiendo esquemas reutilizables y qué riesgo tiene hacerlos demasiado flexibles?
3. ¿Cómo ejecutas la misma suite contra varios ambientes?
4. ¿Qué peligro tiene `callonce`?
5. ¿Cómo decides qué entra en la suite de humo?
