Feature: rutas desconocidas

  Background:
    * url urlBase

  Scenario Outline: <ruta> no existe y responde 404 con un cuerpo de forma conocida
    Given path <ruta>
    When method get
    Then status 404
    And match response == esquemas.rutaDesconocida

    Examples:
      | ruta            |
      | 'no/existe'     |
      | 'salud'         |
      | 'api'           |

  # Defecto: el servidor HTTP del JDK enruta por prefijo, así que /api/notas/abc responde como /api/notas.
  @defecto
  Scenario: una ruta que empieza como una conocida responde como la conocida
    Given path 'api', 'notas', 'abc'
    When method get
    Then status 200
    And match response == esquemas.notas
