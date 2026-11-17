Feature: rutas de salud

  Background:
    * url urlBase

  @humo
  Scenario: vivo responde 200 con el texto vivo
    Given path 'salud', 'vivo'
    When method get
    Then status 200
    And match response == 'vivo'

  @humo
  Scenario: listo responde 200 cuando ya puede atender
    Given path 'salud', 'listo'
    When method get
    Then status 200
    And match response == 'listo'

  @defecto
  Scenario: las rutas de salud no comprueban el método: un DELETE a listo responde 200
    Given path 'salud', 'listo'
    When method delete
    Then status 200
