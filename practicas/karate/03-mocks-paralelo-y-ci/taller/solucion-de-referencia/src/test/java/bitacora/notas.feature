Feature: /api/notas

  Background:
    * url urlBase
    * def sufijo = ' ' + Date.now() + ' ' + Math.random()

  @humo
  Scenario: la lista tiene la forma acordada y todas las notas son texto de 1 a 500 caracteres
    # En Karate 2.1.4 ni '#[] #string' ni "match each" aceptan una lista vacía: se guarda una nota antes.
    * call read('classpath:comun/guardar-nota.feature') { texto: '#("para la forma " + Date.now() + " " + Math.random())' }
    Given path 'api', 'notas'
    When method get
    Then status 200
    And match response == esquemas.notas
    And match each response.notas == '#? _.length >= 1 && _.length <= 500'

  @humo
  Scenario: una nota guardada aparece en la lista
    * def nota = 'nota de prueba' + sufijo
    * def guardada = call read('classpath:comun/guardar-nota.feature') { texto: '#(nota)' }
    * match guardada.response == { guardada: '#(nota)', host: '#regex .+' }

    Given path 'api', 'notas'
    When method get
    Then match response.notas contains nota

  Scenario: dos notas guardadas aparecen en la lista, en el orden en que se guardaron
    * def primera = 'comprar pan' + sufijo
    * def segunda = 'llamar a mamá' + sufijo
    * call read('classpath:comun/guardar-nota.feature') { texto: '#(primera)' }
    * call read('classpath:comun/guardar-nota.feature') { texto: '#(segunda)' }

    Given path 'api', 'notas'
    When method get
    Then match response.notas contains [ '#(primera)', '#(segunda)' ]
    * def posiciones = [ response.notas.indexOf(primera), response.notas.indexOf(segunda) ]
    * match posiciones[0] == '#? _ < posiciones[1]'

  Scenario Outline: se rechaza <caso>
    Given path 'api', 'notas'
    And header X-Clave = clave
    And request cuerpo
    When method post
    Then status 400
    And match response == esquemas.error

    Examples:
      | read('classpath:datos/notas-invalidas.json') |

  Scenario Outline: sin la clave correcta se responde 401 (<caso>) y la nota no se guarda
    * def nota = 'no debería guardarse' + sufijo
    Given path 'api', 'notas'
    And headers <cabeceras>
    And request nota
    When method post
    Then status 401
    And match response == esquemas.error

    Given path 'api', 'notas'
    When method get
    Then match response.notas !contains nota

    Examples:
      | caso                 | cabeceras                     |
      | sin cabecera         | {}                            |
      | con clave incorrecta | { 'X-Clave': 'no-es-la-clave' } |

  Scenario Outline: el método <metodo> no está permitido
    Given path 'api', 'notas'
    And request 'algo'
    When method <metodo>
    Then status 405
    And match response == esquemas.error

    Examples:
      | metodo |
      | put    |
      | delete |
      | patch  |

  Scenario Outline: una nota con <caso> vuelve como JSON válido
    * def nota = <enviada> + sufijo
    * def esperada = <guardada> + sufijo
    Given path 'api', 'notas'
    And header X-Clave = clave
    And request nota
    When method post
    Then status 201
    And match responseType == 'json'
    And match response == { guardada: '#(esperada)', host: '#regex .+' }

    Given path 'api', 'notas'
    When method get
    Then match responseType == 'json'
    And match response.notas contains esperada

    Examples:
      | caso                    | enviada                    | guardada                   |
      | comillas dobles         | 'dijo "hola"'              | 'dijo "hola"'              |
      | barras invertidas       | 'C:\\notas\\uno'           | 'C:\\notas\\uno'           |
      | tildes y eñes           | 'ñandú, café y acción'     | 'ñandú, café y acción'     |
      | emojis                  | 'en bici 🚲 😀'             | 'en bici 🚲 😀'             |
      | un salto de línea       | 'uno\ndos'                 | 'uno dos'                  |
      | un tabulador            | 'uno\tdos'                 | 'uno dos'                  |
      | un carácter de control  | 'uno\u0001dos'             | 'uno\u0001dos'             |
      | etiquetas HTML          | '<b>negrita</b>'           | '<b>negrita</b>'           |
