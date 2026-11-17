Feature: rutas desconocidas

  Background:
    * url urlBase

  @humo
  Scenario: una ruta que no existe responde 404 con un cuerpo de forma conocida
    Given path 'no', 'existe'
    When method get
    Then status 404
    And match response == { error: 'ruta desconocida', rutas: ['/salud/vivo', '/salud/listo', '/api/info', '/api/notas', '/api/carga'] }

  # Defecto: el servidor HTTP del JDK enruta por prefijo, así que /api/notas/abc responde como /api/notas.
  @defecto
  Scenario: una ruta que empieza como una conocida responde como la conocida
    Given path 'api', 'notas', 'abc'
    When method get
    Then status 200
    And match response == { host: '#string', notas: '#[]' }
