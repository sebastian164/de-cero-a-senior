Feature: /api/info

  Background:
    * url urlBase

  @humo
  Scenario: informa su versión y quién respondió
    Given path 'api', 'info'
    When method get
    Then status 200
    And match response == esquemas.info

  Scenario: cada consulta suma al menos una visita
    Given path 'api', 'info'
    When method get
    * def antes = response.visitas

    Given path 'api', 'info'
    When method get
    Then match response.visitas == '#? _ > antes'

  Scenario: guardar una nota sube el número de notas
    Given path 'api', 'info'
    When method get
    * def antes = response.notas
    * call read('classpath:comun/guardar-nota.feature') { texto: '#("para contar " + Date.now() + " " + Math.random())' }

    Given path 'api', 'info'
    When method get
    Then match response.notas == '#? _ >= antes + 1'
