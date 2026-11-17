import { Almacenamiento, AlmacenamientoEnMemoria, AlmacenamientoLocal, ConfiguracionFija } from './almacenamiento';

// La misma batería para el real y el doble: el doble es fiable si pasa lo mismo que el real.
const contrato = (nombre: string, crear: () => Almacenamiento) => describe(`contrato de Almacenamiento: ${nombre}`, () => {
  beforeEach(() => localStorage.clear());
  it('leer una clave inexistente devuelve null (no undefined)', () => expect(crear().leer('nada')).toBeNull());
  it('guarda, lee y elimina', () => {
    const a = crear();
    a.guardar('k', 'v');
    expect(a.leer('k')).toBe('v');
    a.eliminar('k');
    expect(a.leer('k')).toBeNull();
  });
});
contrato('AlmacenamientoLocal', () => new AlmacenamientoLocal());
contrato('AlmacenamientoEnMemoria', () => new AlmacenamientoEnMemoria());

it('ConfiguracionFija solo lee', () => {
  const c = new ConfiguracionFija();
  expect(c.leer('tema')).toBe('claro');
  expect('guardar' in c).toBe(false);
});
