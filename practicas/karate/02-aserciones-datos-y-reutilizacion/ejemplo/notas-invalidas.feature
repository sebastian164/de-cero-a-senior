Feature: validación de notas

  Background:
    * url urlBase
    * def esquemaError = { error: '#string' }

  Scenario Outline: se rechaza <caso>
    Given path 'api', 'notas'
    And header X-Clave = clave
    And request <cuerpo>
    When method post
    Then status <estado>
    And match response == esquemaError

    Examples:
      | caso                        | cuerpo                    | estado |
      | una nota vacía              | ''                        | 400    |
      | una nota de solo espacios   | '     '                   | 400    |
      | una nota de 501 caracteres  | 'x'.repeat(501)           | 400    |

  Scenario Outline: se rechaza el método <metodo>
    Given path 'api', 'notas'
    And header X-Clave = clave
    When method <metodo>
    Then status 405
    And match response == esquemaError

    Examples:
      | metodo |
      | put    |
      | delete |
