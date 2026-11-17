@arranque
Feature: mientras arranca (solo contra una API recién lanzada con RETARDO_ARRANQUE_MS y bitacora.esperar=no)

  Background:
    * url urlBase

  Scenario: mientras arranca está viva pero no lista, y la API rechaza con 503
    Given path 'salud', 'vivo'
    When method get
    Then status 200

    Given path 'salud', 'listo'
    When method get
    Then status 503
    And match response == 'no listo'

    Given path 'api', 'info'
    When method get
    Then status 503
    And match response == { error: '#string' }
