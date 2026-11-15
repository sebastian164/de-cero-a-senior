Feature: el simulador responde con la misma forma que la API real

  Background:
    * url urlDelSimulador
    * def esquemas = call read('esquemas.js')

  Scenario: info
    Given path 'api', 'info'
    When method get
    Then status 200
    And match response == esquemas.info

  Scenario: guardar y listar
    Given path 'api', 'notas'
    And request 'una nota'
    When method post
    Then status 201
    And match response == esquemas.notaGuardada

    Given path 'api', 'notas'
    When method get
    Then match response == esquemas.notas

  Scenario: el error tiene la forma acordada
    Given path 'api', 'notas'
    And request 'provocar-error'
    When method post
    Then status 503
    And match response == esquemas.error
