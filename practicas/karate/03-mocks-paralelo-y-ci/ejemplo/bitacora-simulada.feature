Feature: una Bitácora simulada, para probar la web sin la API real

  Background:
    * def notas = []

  Scenario: pathMatches('/api/info') && methodIs('get')
    * def response = { mensaje: 'Bitácora simulada', version: 'mock', host: 'simulador', visitas: 1, notas: '#(notas.length)' }

  Scenario: pathMatches('/api/notas') && methodIs('get')
    * def response = { host: 'simulador', notas: '#(notas)' }

  Scenario: pathMatches('/api/notas') && methodIs('post') && request == 'provocar-error'
    * def responseStatus = 503
    * def response = { error: 'el servicio no está disponible' }

  Scenario: pathMatches('/api/notas') && methodIs('post')
    * def notas = karate.append(notas, request)
    * def responseStatus = 201
    * def response = { guardada: '#(request)', host: 'simulador' }

  Scenario:
    * def responseStatus = 404
    * def response = { error: 'ruta desconocida' }
