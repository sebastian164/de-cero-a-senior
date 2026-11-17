function fn() {
  var clave = karate.properties['bitacora.clave'];
  if (!clave) {
    karate.fail('Falta la clave de la API: ejecuta con -Dbitacora.clave=<clave>');
  }
  return {
    urlBase: karate.properties['bitacora.url'] || 'http://localhost:48500',
    clave: clave,
    esquemas: {
      notaGuardada: { guardada: '#regex .+', host: '#regex .+', etiquetas: '#[]' },
      error: { error: '#regex .+' }
    }
  };
}
