Feature: listado de notas

  Background:
    * url urlBase
    * def sufijo = ' ' + Date.now()

  Scenario: el listado contiene todas las notas guardadas, y todas son texto
    * def primera = 'comprar pan' + sufijo
    * def segunda = 'llamar a mamá' + sufijo
    * call read('guardar-nota.feature') { texto: '#(primera)' }
    * call read('guardar-nota.feature') { texto: '#(segunda)' }

    Given path 'api', 'notas'
    When method get
    Then status 200
    And match response == { host: '#string', notas: '#[] #string' }
    And match response.notas contains [ '#(primera)', '#(segunda)' ]
    And match each response.notas == '#? _.length <= 500'
