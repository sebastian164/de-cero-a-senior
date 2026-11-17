// Configuración común. Ni la dirección real de un ambiente ni la clave viven en los .feature.
function fn() {
  var clave = karate.properties['bitacora.clave'];
  if (!clave) {
    karate.fail('Falta la clave de la API: ejecuta con -Dbitacora.clave=<clave>');
  }
  var config = {
    urlBase: karate.properties['bitacora.url'] || 'http://localhost:48080',
    clave: clave
  };
  // Antes de la primera prueba se espera a que la API esté lista, reintentando: nada de esperas fijas.
  if (karate.properties['bitacora.esperar'] != 'no') {
    karate.callSingle('classpath:comun/esperar-lista.feature', config);
  }
  return config;
}
