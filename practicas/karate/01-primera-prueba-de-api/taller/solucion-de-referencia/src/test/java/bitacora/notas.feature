Feature: /api/notas

  Background:
    * url urlBase
    * def esquemaLista = { host: '#string', notas: '#[] #string' }
    * def esquemaError = { error: '#string' }
    * def unica = function(texto) { return texto + ' ' + Date.now() + ' ' + Math.random() }

  @humo
  Scenario: la lista tiene la forma acordada
    Given path 'api', 'notas'
    When method get
    Then status 200
    And match response == esquemaLista

  @humo
  Scenario: una nota guardada aparece en la lista
    * def nota = unica('nota de prueba')
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

  @humo
  Scenario: guardar una nota sin clave responde 401
    Given path 'api', 'notas'
    And request 'una nota sin clave'
    When method post
    Then status 401
    And match response == esquemaError

  Scenario: guardar una nota con una clave incorrecta responde 401 y no la guarda
    * def nota = unica('con clave mala')
    Given path 'api', 'notas'
    And header X-Clave = clave + '-incorrecta'
    And request nota
    When method post
    Then status 401
    And match response == esquemaError

    Given path 'api', 'notas'
    When method get
    Then match response.notas !contains nota

  Scenario: una nota vacía se rechaza
    Given path 'api', 'notas'
    And header X-Clave = clave
    And request ''
    When method post
    Then status 400
    And match response == esquemaError

  Scenario: una nota de solo espacios se rechaza
    Given path 'api', 'notas'
    And header X-Clave = clave
    And request '     '
    When method post
    Then status 400
    And match response == esquemaError

  Scenario: una nota de 500 caracteres se acepta
    * def nota = 'x'.repeat(500)
    Given path 'api', 'notas'
    And header X-Clave = clave
    And request nota
    When method post
    Then status 201

  Scenario: una nota de 501 caracteres se rechaza
    Given path 'api', 'notas'
    And header X-Clave = clave
    And request 'x'.repeat(501)
    When method post
    Then status 400
    And match response == esquemaError

  Scenario: una nota con comillas, barras y tildes vuelve igual
    * def nota = unica('dijo "hola", ñandú y café \\ barra')
    Given path 'api', 'notas'
    And header X-Clave = clave
    And request nota
    When method post
    Then status 201
    And match response.guardada == nota

    Given path 'api', 'notas'
    When method get
    Then match response.notas contains nota

  Scenario: los espacios repetidos y los saltos de línea se reducen a un espacio
    * def sufijo = Date.now() + ' ' + Math.random()
    Given path 'api', 'notas'
    And header X-Clave = clave
    And request 'una\nnota   con    huecos ' + sufijo
    When method post
    Then status 201
    And match response.guardada == 'una nota con huecos ' + sufijo

  Scenario: PUT no está permitido
    Given path 'api', 'notas'
    And request 'algo'
    When method put
    Then status 405
    And match response == esquemaError

  Scenario: DELETE no está permitido
    Given path 'api', 'notas'
    When method delete
    Then status 405
    And match response == esquemaError
