Feature: API de la bitácora

  Background:
    * url urlBase

  Scenario: informa su versión y quién respondió
    Given path 'api', 'info'
    When method get
    Then status 200
    And match response == { mensaje: '#string', version: '#string', host: '#string', visitas: '#number', notas: '#number' }
    And match response.visitas == '#? _ > 0'

  Scenario: guardar una nota exige la clave
    Given path 'api', 'notas'
    And request 'una nota sin clave'
    When method post
    Then status 401
    And match response == { error: '#string' }

  Scenario: una nota guardada aparece en la lista
    * def nota = 'nota de prueba ' + Date.now()

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

  Scenario: una nota vacía se rechaza
    Given path 'api', 'notas'
    And header X-Clave = clave
    And request '   '
    When method post
    Then status 400
