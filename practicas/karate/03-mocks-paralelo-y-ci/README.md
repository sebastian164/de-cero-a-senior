# 03 — Mocks, ejecución en paralelo e integración continua

Los archivos de esta sesión se ejecutaron con Karate 2.1.4 y JDK 21. Las órdenes para arrancar un servidor simulado y para ejecutar en paralelo cambiaron entre la serie 1 y la 2 de Karate: tómalas de la documentación de tu versión. En 2.1.4 funcionan así: `MockServer.feature("classpath:simulador/bitacora-simulada.feature").port(48090).start()` y `Runner.path("classpath:bitacora").parallel(4)`, los dos de `io.karatelabs.core`.

## Idea

Un ensayo general con suplentes. Si el actor principal está de gira, la compañía no cancela el ensayo: alguien lee su papel con el guion en la mano. No actúa bien, pero dice las frases correctas en el momento correcto, y eso basta para que los demás practiquen sus entradas. Un servidor simulado es ese suplente: responde lo que el guion dice, para que tú puedas probar tu parte sin esperar a nadie.

## Lo esencial

### Servidores simulados

Karate puede levantar un servidor HTTP que responde según un archivo `.feature`. Cada escenario es una regla: si la petición cumple la condición, se responde con lo que el escenario define.

| Sirve para | No sirve para |
|---|---|
| Desarrollar un cliente cuando el servicio aún no existe | Comprobar que el servicio real funciona |
| Probar cómo reacciona tu sistema ante errores difíciles de provocar (un 503, una respuesta lenta, un JSON roto) | Sustituir las pruebas contra el servicio real |
| Aislar tus pruebas de un tercero inestable o de pago | |

El riesgo de todo simulador es que **se quede atrás**: el servicio real cambia y el simulado sigue respondiendo como antes, con todas tus pruebas en verde. La defensa es usar los **mismos esquemas** en dos sitios: en las pruebas que corren contra el servicio real y en el simulador. Si el real cambia, las primeras fallan y te avisan de que el simulador miente.

### Ejecución en paralelo

Karate ejecuta archivos y escenarios en varios hilos. Una suite de diez minutos puede bajar a dos. Eso exige lo que ya vienes cuidando:

- Cada escenario crea sus propios datos, con valores únicos.
- Nada depende del orden.
- No se comprueba "hay exactamente tres notas", sino "las mías están".

Para los pocos escenarios que no pueden coincidir con otros (porque tocan una configuración global, por ejemplo), la serie 2 ofrece una etiqueta de bloqueo.

### En la integración continua

| Paso | Detalle |
|---|---|
| Levantar el sistema | Con Compose, en el propio flujo |
| Esperar a que esté listo | Consultando su ruta de salud, no con una espera fija |
| Ejecutar | Humo en cada cambio; la suite completa antes de fusionar |
| Publicar el informe | Como artefacto del flujo, también cuando falla |
| Fallar el flujo | Si falla un escenario |

### Lo que Karate también hace, y lo que no

El mismo proyecto incluye pruebas de rendimiento (reutilizando los `.feature` con Gatling) y automatización de navegador. Vale la pena saber que existen. No es una herramienta para pruebas unitarias ni reemplaza a las pruebas de contrato dirigidas por el consumidor.

## Ejemplo

### Antes

El equipo del frontend no puede probar su página contra una API que falla, porque la API real nunca falla cuando se la necesita. Así que escribe en el código del frontend: `if (modoPrueba) return { mensaje: 'falso' }`. Ese `if` llega a producción. Mientras tanto, las pruebas de la API corren en serie, tardan doce minutos y por eso solo se ejecutan los viernes.

### Análisis

| Problema | Consecuencia |
|---|---|
| Respuestas falsas dentro del código de producción | Código muerto, y el riesgo de que se active donde no debe |
| El caso de error nunca se prueba | La primera vez que la API falla, es en producción |
| Suite lenta | Se ejecuta poco, y un fallo se descubre días después del cambio que lo causó |
| El simulador improvisado no sigue ningún contrato | Nadie sabe si se parece a la API real |

### Después

<!-- file: ejemplo/esquemas.js -->
```javascript
// La forma de cada respuesta, definida una vez. La usan las pruebas contra la API real y el simulador.
function fn() {
  return {
    info: { mensaje: '#string', version: '#string', host: '#string', visitas: '#number', notas: '#number' },
    notas: { host: '#string', notas: '#[] #string' },
    notaGuardada: { guardada: '#string', host: '#string' },
    error: { error: '#string' }
  };
}
```

Este esquema de `notas` no acepta la lista vacía en Karate 2.1.4 (véase la sesión 02): guarda una nota antes de usarlo, o comprueba la lista con `'#[]'`.

<!-- file: ejemplo/bitacora-simulada.feature -->
```gherkin
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
```

<!-- file: ejemplo/el-simulador-cumple-el-contrato.feature -->
```gherkin
Feature: el simulador responde con la misma forma que la API real

  Background:
    * url urlDelSimulador
    * def esquemas = call read('esquemas.js')

  Scenario: info
    Given path 'api', 'info'
    When method get
    Then status 200
    And match response == esquemas.info

  Scenario: guardar y listar
    Given path 'api', 'notas'
    And request 'una nota'
    When method post
    Then status 201
    And match response == esquemas.notaGuardada

    Given path 'api', 'notas'
    When method get
    Then match response == esquemas.notas

  Scenario: el error tiene la forma acordada
    Given path 'api', 'notas'
    And request 'provocar-error'
    When method post
    Then status 503
    And match response == esquemas.error
```

- El frontend apunta al simulador en sus pruebas. Su código no sabe que existe.
- El error 503 se puede provocar cuando se quiera.
- Los esquemas son los mismos que usan las pruebas contra la API real: si la API cambia, esas pruebas fallan y delatan al simulador.

## Errores comunes

- Probar solo contra simuladores y creer que el sistema funciona.
- Un simulador con lógica de negocio: acaba siendo una segunda implementación que mantener.
- Simuladores que nadie compara con el servicio real.
- Activar el paralelismo con escenarios que comparten datos.
- Marcar como "inestable" una prueba que falla a veces, en vez de averiguar por qué.
- Esperas fijas en la integración continua.
- No publicar el informe cuando la suite falla, que es justo cuando hace falta.

## Taller integrador de Karate

**Objetivo:** dejar la suite de Bitácora corriendo en paralelo y en integración continua, con un simulador que el frontend pueda usar.

1. **Paralelo.** Ejecuta tu suite en varios hilos siguiendo la documentación de tu versión. Mide el tiempo con uno, dos, cuatro y ocho. ¿Dónde deja de mejorar y por qué?
2. Si algún escenario falla solo en paralelo, encuentra el dato compartido y arréglalo. No lo excluyas.
3. Ejecuta la suite en paralelo veinte veces seguidas. Debe pasar las veinte. Si falla una, tienes una prueba inestable: averigua la causa.
4. **Simulador.** Escribe un servidor simulado de Bitácora que cubra todas las rutas y permita provocar, a voluntad: un 503, una respuesta que tarda cinco segundos y un cuerpo que no es JSON válido.
5. Arranca la web de Bitácora (el contenedor de nginx) apuntando al simulador. Comprueba a mano qué hace la página en cada uno de los tres fallos. Anota los defectos de la página que encuentres.
6. **Contrato.** Mueve los esquemas a un archivo común. Ejecuta la misma comprobación de formas contra la API real y contra tu simulador.
7. Cambia un campo en la API real. ¿Qué prueba te avisa de que el simulador quedó desactualizado? Si ninguna, falta una.
8. **Integración continua.** Escribe un flujo que construya la imagen de la API, la levante con Compose, espere a `/salud/listo`, ejecute la suite de humo en cada cambio y la completa en los pull requests, y publique el informe siempre.
9. Introduce un defecto en la API en una rama. El flujo debe fallar y el informe debe decir qué escenario y por qué.
10. Añade una prueba de rendimiento básica reutilizando un `.feature` existente, siguiendo la documentación. ¿Qué te dice y qué no te dice una prueba de carga contra tu portátil?
11. Escribe para tu equipo, en una página: qué se prueba con Karate, qué no, cuándo corre cada parte y quién arregla una prueba roja.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| La suite pasa veinte veces seguidas en paralelo | Paso 3 |
| El simulador y la API real cumplen los mismos esquemas | Paso 6 |
| Un cambio incompatible en la API se detecta | Paso 7 |
| El flujo falla con un defecto y publica el informe | Paso 9 |

**Entrega:** rama `taller/karate-03`, con el proyecto y el flujo en `taller/solucion/` y `ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

Las pruebas de API son también el puente con la parte siguiente de esta ruta: un criterio de aceptación bien escrito en una especificación se convierte, casi palabra por palabra, en un escenario de Karate. Cuando eso ocurre, la especificación deja de ser un documento y pasa a ser algo que se ejecuta.

## Preguntas senior

1. ¿Para qué sirve un servidor simulado y qué no demuestra?
2. ¿Cómo evitas que un simulador se desvíe del servicio real?
3. ¿Qué exige de tus pruebas la ejecución en paralelo?
4. ¿Qué haces con una prueba que falla una de cada veinte veces?
5. ¿Qué pruebas corres en cada cambio y cuáles no, y por qué?
