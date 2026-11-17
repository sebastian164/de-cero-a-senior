// Configuración común. Las direcciones de cada ambiente viven aquí; la clave llega siempre por parámetro.
function fn() {
  var urls = {
    local: 'http://localhost:48080',        // java Servidor.java en tu máquina
    contenedor: 'http://localhost:48081'    // docker compose up, con compose.yaml
  };
  var ambiente = karate.env || 'local';
  if (!urls[ambiente]) {
    karate.fail('Ambiente desconocido: ' + ambiente + '. Usa uno de: ' + Object.keys(urls));
  }
  var clave = karate.properties['bitacora.clave'];
  if (!clave) {
    karate.fail('Falta la clave de la API: ejecuta con -Dbitacora.clave=<clave>');
  }
  var config = {
    ambiente: ambiente,
    urlBase: karate.properties['bitacora.url'] || urls[ambiente],
    clave: clave,
    esquemas: karate.call('classpath:esquemas.js')
  };
  if (karate.properties['bitacora.esperar'] != 'no') {
    karate.callSingle('classpath:comun/esperar-lista.feature', config);
  }
  return config;
}
