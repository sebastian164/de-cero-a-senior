@ignore
Feature: guardar una nota (se llama desde info.feature, notas.feature y falla-a-proposito.feature)

  Scenario:
    Given url urlBase
    And path 'api', 'notas'
    And header X-Clave = clave
    And request texto
    When method post
    Then status 201
    And match response == esquemas.notaGuardada
