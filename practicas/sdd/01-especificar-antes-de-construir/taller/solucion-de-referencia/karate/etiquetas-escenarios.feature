Feature: etiquetas en las notas (paso 9: tres criterios convertidos en escenarios, sin implementar nada todavía)

  Background:
    * url urlBase
    * def sufijo = ' ' + Date.now() + ' ' + Math.random()

  # Criterio 4 (y 2, 3): respuesta de guardar con las etiquetas normalizadas, sin repetir, en orden de aparición.
  Scenario: guardar una nota devuelve sus etiquetas en minúsculas, sin repetir y en orden
    Given path 'api', 'notas'
    And header X-Clave = clave
    And request 'reunión #Trabajo y #casa otra vez #TRABAJO' + sufijo
    When method post
    Then status 201
    And match response == { guardada: '#string', host: '#string', etiquetas: ['trabajo', 'casa'] }

  # Criterio 7 y 9: filtrar devuelve solo las de esa etiqueta, en el orden guardado.
  Scenario: consultar por etiqueta devuelve solo esas notas, en orden
    * def marca = 'e' + Date.now()
    * def a = 'primera #' + marca + ' ' + Math.random()
    * def b = 'sin etiqueta ' + Math.random()
    * def c = 'tercera #' + marca + ' ' + Math.random()
    * call read('classpath:comun/guardar-nota.feature') { texto: '#(a)' }
    * call read('classpath:comun/guardar-nota.feature') { texto: '#(b)' }
    * call read('classpath:comun/guardar-nota.feature') { texto: '#(c)' }
    Given path 'api', 'notas'
    And param etiqueta = marca
    When method get
    Then status 200
    And match response == { host: '#string', notas: ['#(a)', '#(c)'] }

  # Criterio 11: etiqueta inválida.
  Scenario Outline: una etiqueta inválida se rechaza (<caso>)
    Given path 'api', 'notas'
    And param etiqueta = valor
    When method get
    Then status 400
    And match response == { error: '#string' }

    Examples:
      | caso           | valor                                 |
      | vacía          |                                       |
      | con espacio    | dos palabras                          |
      | 31 caracteres  | aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa       |
      | signo no válido| a.b                                   |
