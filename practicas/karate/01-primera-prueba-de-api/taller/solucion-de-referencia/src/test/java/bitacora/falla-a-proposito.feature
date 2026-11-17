@falla
Feature: un fallo provocado, para ver qué cuenta el informe HTML (paso 11)

  Background:
    * url urlBase

  Scenario: la versión debería ser 9.9.9
    Given path 'api', 'info'
    When method get
    Then status 200
    And match response.version == '9.9.9'
