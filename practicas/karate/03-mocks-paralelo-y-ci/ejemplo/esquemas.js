// La forma de cada respuesta, definida una vez. La usan las pruebas contra la API real y el simulador.
function fn() {
  return {
    info: { mensaje: '#string', version: '#string', host: '#string', visitas: '#number', notas: '#number' },
    notas: { host: '#string', notas: '#[] #string' },
    notaGuardada: { guardada: '#string', host: '#string' },
    error: { error: '#string' }
  };
}
