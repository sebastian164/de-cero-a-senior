Feature: la forma de las respuestas, la misma contra la API real y contra el simulador

  # Se ejecuta con -Dbitacora.url=<API real o simulador>. Los esquemas son los de esquemas.js, un solo archivo.
  Background:
    * url urlBase

  Scenario: info
    Given path 'api', 'info'
    When method get
    Then status 200
    And match response == esquemas.info

  Scenario: guardar y listar
    Given path 'api', 'notas'
    And header X-Clave = clave
    And request 'una nota ' + Date.now()
    When method post
    Then status 201
    And match response == esquemas.notaGuardada

    Given path 'api', 'notas'
    When method get
    Then status 200
    And match response == esquemas.notas

  Scenario: sin clave, 401 con la forma del error
    Given path 'api', 'notas'
    And request 'sin clave'
    When method post
    Then status 401
    And match response == esquemas.error

  Scenario: nota vacía, 400 con la forma del error
    Given path 'api', 'notas'
    And header X-Clave = clave
    And request '   '
    When method post
    Then status 400
    And match response == esquemas.error

  Scenario: método no permitido, 405
    Given path 'api', 'notas'
    And request 'algo'
    When method put
    Then status 405
    And match response == esquemas.error

  Scenario: carga
    Given path 'api', 'carga'
    And param ms = 10
    When method get
    Then status 200
    And match response == esquemas.carga

  Scenario: ruta desconocida
    Given path 'api', 'nada'
    When method get
    Then status 404
    And match response == esquemas.rutaDesconocida
