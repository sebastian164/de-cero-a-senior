Feature: /api/carga

  Background:
    * url urlBase
    * def esquemaCarga = { host: '#string', ms: '#number', resultado: '#number' }

  Scenario: gasta el tiempo pedido y lo informa
    Given path 'api', 'carga'
    And param ms = 300
    When method get
    Then status 200
    And match response == esquemaCarga
    And match response.ms == 300
    And assert responseTime >= 300

  Scenario: sin parámetro gasta 200 ms
    Given path 'api', 'carga'
    When method get
    Then status 200
    And match response.ms == 200

  @lento
  Scenario: más de 5000 ms se recorta a 5000
    Given path 'api', 'carga'
    And param ms = 999999
    When method get
    Then status 200
    And match response.ms == 5000
    And assert responseTime >= 5000

  Scenario: un valor negativo se trata como 0
    Given path 'api', 'carga'
    And param ms = -5
    When method get
    Then status 200
    And match response == { host: '#string', ms: 0, resultado: 0 }

  # Lo que debería pasar: un 400 que diga que ms no es un número. Lo que pasa: se ignora y se usan 200 ms.
  @defecto
  Scenario: un ms que no es número no da error: se usan 200 ms
    Given path 'api', 'carga'
    And param ms = 'abc'
    When method get
    Then status 200
    And match response.ms == 200
