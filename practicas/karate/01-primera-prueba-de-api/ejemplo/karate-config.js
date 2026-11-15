// Configuración común. Karate la ejecuta antes de cada escenario y deja el resultado como variables.
function fn() {
  var ambiente = karate.env || 'local';        // se elige con -Dkarate.env=pruebas

  var config = {
    urlBase: 'http://localhost:8080',
    clave: karate.properties['bitacora.clave'] || 'secreta'   // la real llega por parámetro, no vive en el repositorio
  };

  if (ambiente === 'pruebas') {
    config.urlBase = 'https://bitacora.pruebas.example';
  }

  return config;
}
