@ignore
Feature: esperar a que Bitácora esté lista, sin una espera fija

  Scenario:
    * configure retry = { count: 150, interval: 200 }
    Given url urlBase
    And path 'salud', 'listo'
    And retry until responseStatus == 200
    When method get
