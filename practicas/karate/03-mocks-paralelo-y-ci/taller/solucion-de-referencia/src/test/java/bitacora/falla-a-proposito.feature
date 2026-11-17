@falla
Feature: una regla más estricta que la API, para ver el mensaje de match each (paso 8)

  Background:
    * url urlBase

  Scenario: todas las notas tienen al menos tres caracteres
    * call read('classpath:comun/guardar-nota.feature') { texto: 'ab' }
    Given path 'api', 'notas'
    When method get
    Then match each response.notas == '#? _.length >= 3'
