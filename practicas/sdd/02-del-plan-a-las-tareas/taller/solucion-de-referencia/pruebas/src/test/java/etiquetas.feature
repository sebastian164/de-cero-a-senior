Feature: etiquetas en las notas. Una prueba por criterio de la especificación (escrita antes que el código)

  Background:
    * url urlBase
    * def sufijo = 'k' + Date.now() + Math.floor(Math.random() * 100000)
    * def guardar = function(texto){ return karate.call('classpath:comun/guardar-nota.feature', { texto: texto }) }

  @criterio-1 @criterio-2 @criterio-3 @criterio-4 @criterio-5
  Scenario Outline: se reconocen las etiquetas de <caso>
    Given path 'api', 'notas'
    And header X-Clave = clave
    And request texto + ' ' + sufijo
    When method post
    Then status 201
    And match response == { guardada: '#string', host: '#string', etiquetas: '#(etiquetas)' }

    Examples:
      | caso | texto | etiquetas! |
      | una etiqueta | ir al banco #trabajo | ['trabajo'] |
      | # dentro de una palabra | a#b #c | ['c'] |
      | # solo | # | [] |
      | doble # | ##x | [] |
      | # y espacio | # x | [] |
      | signos pegados | #trabajo, #casa. | ['trabajo', 'casa'] |
      | mayúsculas | #Trabajo | ['trabajo'] |
      | repetidas | #a #A #a | ['a'] |
      | orden de aparición | x #Uno #dos #UNO | ['uno', 'dos'] |
      | sin etiquetas | sin etiquetas | [] |
      | 31 caracteres | #aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa | [] |
      | 30 caracteres | #aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa | ['aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'] |
      | tildes distinguen | #Café #cafe | ['café', 'cafe'] |
      | guion y guion bajo | #a-b_c | ['a-b_c'] |


  @criterio-6
  Scenario: más de 10 etiquetas distintas se rechaza y la nota no se guarda
    * def once = '#a1 #a2 #a3 #a4 #a5 #a6 #a7 #a8 #a9 #a10 #a11 ' + sufijo
    Given path 'api', 'notas'
    And header X-Clave = clave
    And request once
    When method post
    Then status 400
    And match response == esquemas.error
    Given path 'api', 'notas'
    When method get
    Then match response.notas !contains once

  @criterio-6
  Scenario: diez etiquetas distintas se aceptan, aunque una se repita
    Given path 'api', 'notas'
    And header X-Clave = clave
    And request '#a1 #a2 #a3 #a4 #a5 #a6 #a7 #a8 #a9 #a10 #A10 ' + sufijo
    When method post
    Then status 201
    And match response.etiquetas == ['a1','a2','a3','a4','a5','a6','a7','a8','a9','a10']

  @criterio-7 @criterio-8 @criterio-9
  Scenario: consultar por etiqueta devuelve solo esas notas, en el orden guardado, con o sin # y sin importar mayúsculas
    * def marca = 'tema' + sufijo
    * def a = 'primera #' + marca + ' uno'
    * def b = 'sin etiqueta ' + sufijo
    * def c = 'tercera #' + marca.toUpperCase() + ' dos'
    * guardar(a)
    * guardar(b)
    * guardar(c)
    Given path 'api', 'notas'
    And param etiqueta = marca
    When method get
    Then status 200
    And match response == { host: '#string', notas: ['#(a)', '#(c)'] }
    * def base = response
    Given path 'api', 'notas'
    And param etiqueta = '#' + marca
    When method get
    Then match response == base
    Given path 'api', 'notas'
    And param etiqueta = marca.toUpperCase()
    When method get
    Then match response == base

  @criterio-10
  Scenario: una etiqueta que nadie tiene devuelve una lista vacía
    Given path 'api', 'notas'
    And param etiqueta = 'nadie' + sufijo
    When method get
    Then status 200
    And match response == { host: '#string', notas: [] }

  @criterio-11
  Scenario Outline: una etiqueta inválida en la consulta se rechaza (<caso>)
    # La dirección se arma a mano: así el parámetro vacío (?etiqueta=) viaja de verdad.
    Given url urlBase + '/api/notas?etiqueta=' + encodeURIComponent(valor == 'vacío' ? '' : valor)
    When method get
    Then status 400
    And match response == esquemas.error

    Examples:
      | caso            | valor                             |
      | vacía           | vacío                             |
      | solo #          | #                                 |
      | con espacio     | a b                               |
      | 31 caracteres   | aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa   |
      | punto           | a.b                               |
      | dos #           | ##a                               |

  @criterio-11
  Scenario: treinta caracteres, con o sin #, son una consulta válida
    Given path 'api', 'notas'
    And param etiqueta = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
    When method get
    Then status 200
    Given path 'api', 'notas'
    And param etiqueta = '#aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
    When method get
    Then status 200

  @criterio-12 @criterio-13
  Scenario: sin parámetro la respuesta es la de siempre, y con etiqueta tiene la misma forma
    Given path 'api', 'notas'
    When method get
    Then status 200
    And match response == { host: '#string', notas: '#[] #string' }
    Given path 'api', 'notas'
    And param etiqueta = 'x'
    When method get
    Then match response == { host: '#string', notas: '#[]' }

  @criterio-14
  Scenario: una nota guardada sin etiquetas explícitas pero con #algo en el texto aparece al filtrar (el filtro lee el texto, no un índice aparte)
    * def marca = 'legado' + sufijo
    * def texto = 'viejo #' + marca
    * guardar(texto)
    Given path 'api', 'notas'
    And param etiqueta = marca
    When method get
    Then match response.notas == ['#(texto)']

  @criterio-15
  Scenario Outline: guardar exige la clave (<caso>) y consultar no
    Given path 'api', 'notas'
    And headers <cabeceras>
    And request '#x ' + sufijo
    When method post
    Then status 401
    Given path 'api', 'notas'
    And param etiqueta = 'x'
    When method get
    Then status 200

    Examples:
      | caso      | cabeceras                      |
      | sin clave | {}                             |
      | incorrecta| { 'X-Clave': 'no-es-la-clave' } |
