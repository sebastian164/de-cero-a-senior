@lento
Feature: /api/carga (todos sus escenarios usan /api/carga: @lento)

  Background:
    * url urlBase

  Scenario Outline: con ms=<ms> gasta <esperado> ms y lo informa
    Given path 'api', 'carga'
    And param ms = '<ms>'
    When method get
    Then status 200
    And match response == esquemas.carga
    And match response.ms == <esperado>
    And assert responseTime >= <esperado>

    Examples:
      | ms  | esperado |
      | 300 | 300      |
      | 0   | 0        |
      | -5  | 0        |
      | abc | 200      |

    Examples:
      | ms     | esperado |
      | 999999 | 5000     |

  Scenario: sin parámetro gasta 200 ms
    Given path 'api', 'carga'
    When method get
    Then status 200
    And match response == esquemas.carga
    And match response.ms == 200
