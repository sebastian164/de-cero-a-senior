@esquemas
Feature: los esquemas rechazan respuestas rotas (paso 10)

  Cada esquema se prueba "al revés": respuestas que NO deben cumplirlo. Si alguna lo cumpliera, el esquema sería demasiado permisivo.

  Background:
    * def esquemas = karate.call('classpath:esquemas.js')

  Scenario Outline: el esquema info rechaza <caso>
    * match <roto> != esquemas.info

    Examples:
      | caso                        | roto                                                                                  |
      | un cuerpo vacío             | ''                                                                                    |
      | un objeto vacío             | {}                                                                                    |
      | un campo menos              | { mensaje: 'a', version: 'dev', host: 'h', visitas: 1 }                               |
      | un campo de más             | { mensaje: 'a', version: 'dev', host: 'h', visitas: 1, notas: 0, extra: true }        |
      | visitas como texto          | { mensaje: 'a', version: 'dev', host: 'h', visitas: 'uno', notas: 0 }                 |
      | visitas en cero             | { mensaje: 'a', version: 'dev', host: 'h', visitas: 0, notas: 0 }                     |
      | una versión sin forma       | { mensaje: 'a', version: 'x', host: 'h', visitas: 1, notas: 0 }                       |
      | notas negativas             | { mensaje: 'a', version: 'dev', host: 'h', visitas: 1, notas: -1 }                    |
      | host vacío                  | { mensaje: 'a', version: 'dev', host: '', visitas: 1, notas: 0 }                      |
      | mensaje vacío               | { mensaje: '', version: 'dev', host: 'h', visitas: 1, notas: 0 }                      |

  Scenario Outline: los demás esquemas rechazan <caso>
    * match <roto> != esquemas.<esquema>

    Examples:
      | caso                           | esquema      | roto                                           |
      | notas sin la lista             | notas        | { host: 'h' }                                  |
      | notas con un número en la lista | notas       | { host: 'h', notas: ['a', 1] }                 |
      | nota guardada vacía            | notaGuardada | { guardada: '', host: 'h' }                    |
      | carga con ms fuera de rango    | carga        | { host: 'h', ms: 6000, resultado: 1 }          |
      | error sin texto                | error        | { error: '' }                                  |
      | ruta desconocida con otra lista | rutaDesconocida | { error: 'ruta desconocida', rutas: [] }    |

  Scenario: el esquema notas acepta la lista vacía, que es una respuesta válida de una API recién arrancada
    * match { host: 'h', notas: [] } == esquemas.notas
