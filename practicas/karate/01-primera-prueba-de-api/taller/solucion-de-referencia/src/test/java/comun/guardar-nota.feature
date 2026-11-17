@ignore
Feature: guardar una nota (para llamar desde otros archivos)

  Scenario:
    Given url urlBase
    And path 'api', 'notas'
    And header X-Clave = clave
    And request texto
    When method post
    Then status 201
