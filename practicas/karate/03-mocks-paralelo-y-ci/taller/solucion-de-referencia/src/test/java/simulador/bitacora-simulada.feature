Feature: Bitácora simulada. Cubre todas las rutas y se puede romper a voluntad con PUT /control/modo

  # El modo se cambia con:  curl -X PUT http://localhost:48090/control/modo -d 503
  #   normal  responde bien
  #   503     toda la API responde 503
  #   lento   responde bien, pero tarda cinco segundos
  #   roto    responde 200 con Content-Type JSON y un cuerpo que no es JSON válido
  Background:
    * def modo = 'normal'
    * def notas = []
    * def demora = function(){ return modo == 'lento' ? 5000 : 0 }
    * def claves = ['normal', '503', 'lento', 'roto']

  Scenario: pathMatches('/control/modo') && methodIs('put')
    * def nuevo = karate.toString(request).trim()
    * def valido = claves.indexOf(nuevo) >= 0
    * def modo = valido ? nuevo : modo
    * def responseStatus = valido ? 200 : 400
    * def response = { modo: '#(modo)' }

  Scenario: (pathMatches('/api/{x}') || pathMatches('/salud/{x}')) && modo == '503'
    * def responseStatus = 503
    * def response = { error: 'el servicio no está disponible' }

  Scenario: pathMatches('/api/{x}') && modo == 'roto'
    * def responseStatus = 200
    * def responseHeaders = { 'Content-Type': 'application/json' }
    * def response = '{"mensaje": "cortado a la mitad", "versi'

  Scenario: pathMatches('/salud/vivo') && methodIs('get')
    * def responseHeaders = { 'Content-Type': 'text/plain' }
    * def response = 'vivo'

  Scenario: pathMatches('/salud/listo') && methodIs('get')
    * def responseHeaders = { 'Content-Type': 'text/plain' }
    * def response = 'listo'

  Scenario: pathMatches('/api/info') && methodIs('get')
    * def responseDelay = demora()
    * def response = { mensaje: 'Bitácora simulada', version: '1.0.0', host: 'simulador', visitas: 1, notas: '#(notas.length)' }

  Scenario: pathMatches('/api/notas') && methodIs('get')
    * def responseDelay = demora()
    * def response = { host: 'simulador', notas: '#(notas)' }

  Scenario: pathMatches('/api/notas') && methodIs('post') && !requestHeaders['X-Clave']
    * def responseStatus = 401
    * def response = { error: 'clave ausente o incorrecta' }

  Scenario: pathMatches('/api/notas') && methodIs('post') && karate.toString(request).trim() == ''
    * def responseStatus = 400
    * def response = { error: 'la nota debe tener entre 1 y 500 caracteres' }

  Scenario: pathMatches('/api/notas') && methodIs('post')
    * def responseDelay = demora()
    * def nota = karate.toString(request).trim()
    * def notas = karate.append(notas, nota)
    * def responseStatus = 201
    * def response = { guardada: '#(nota)', host: 'simulador' }

  Scenario: pathMatches('/api/notas')
    * def responseStatus = 405
    * def response = { error: 'método no permitido' }

  Scenario: pathMatches('/api/carga')
    * def responseDelay = demora()
    * def response = { host: 'simulador', ms: 200, resultado: 1 }

  Scenario:
    * def responseStatus = 404
    * def response = { error: 'ruta desconocida', rutas: ['/salud/vivo', '/salud/listo', '/api/info', '/api/notas', '/api/carga'] }
