Feature: rutas de salud

  Background:
    * url urlBase

  @humo
  Scenario: vivo responde 200 con el texto vivo
    Given path 'salud', 'vivo'
    When method get
    Then status 200
    And match response == 'vivo'
    And match header Content-Type contains 'text/plain'

  @humo
  Scenario: listo responde 200 cuando ya puede atender
    Given path 'salud', 'listo'
    When method get
    Then status 200
    And match response == 'listo'

  Scenario: una ruta de salud que no existe responde 404 con la lista de rutas
    Given path 'salud'
    When method get
    Then status 404
    And match response == { error: 'ruta desconocida', rutas: '#[5] #string' }

  @defecto
  Scenario: las rutas de salud no comprueban el método: un DELETE a listo responde 200
    Given path 'salud', 'listo'
    When method delete
    Then status 200
