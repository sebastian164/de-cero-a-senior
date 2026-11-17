// La forma de cada respuesta de Bitácora, definida una sola vez. La usan todos los escenarios.
function fn() {
  return {
    info: {
      mensaje: '#string',
      version: '#regex \\d+\\.\\d+\\.\\d+|dev',
      host: '#regex .+',
      visitas: '#? _ >= 1',
      notas: '#? _ >= 0'
    },
    // La lista puede venir vacía (API recién arrancada): el esquema acepta [] y rechaza cualquier elemento que no sea texto.
    notas: { host: '#regex .+', notas: '#? Array.isArray(_) && _.every(x => typeof x == "string")' },
    notaGuardada: { guardada: '#regex .+', host: '#regex .+' },
    carga: { host: '#regex .+', ms: '#? _ >= 0 && _ <= 5000', resultado: '#? _ >= 0' },
    error: { error: '#regex .+' },
    rutaDesconocida: { error: 'ruta desconocida', rutas: ['/salud/vivo', '/salud/listo', '/api/info', '/api/notas', '/api/carga'] }
  };
}
