Feature: /api/info

  Background:
    * url urlBase
    * def esquemaInfo = { mensaje: '#string', version: '#string', host: '#string', visitas: '#number', notas: '#number' }

  @humo
  Scenario: informa su versión y quién respondió
    Given path 'api', 'info'
    When method get
    Then status 200
    And match response == esquemaInfo

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

  @defecto
  Scenario: info no comprueba el método: un POST responde 200 como un GET
    Given path 'api', 'info'
    And request 'cualquier cosa'
    When method post
    Then status 200
    And match response == esquemaInfo
