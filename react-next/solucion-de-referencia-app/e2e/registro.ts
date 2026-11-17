// Guarda mediciones por clave, mezclando con lo ya escrito (un fallo reinicia el proceso de trabajo y se perdería lo anterior).
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';

export function guardarMedicion(archivo: string, clave: string, valor: unknown) {
  mkdirSync('mediciones', { recursive: true });
  const ruta = `mediciones/${archivo}`;
  const actual = existsSync(ruta) ? JSON.parse(readFileSync(ruta, 'utf8')) : {};
  actual[clave] = valor;
  writeFileSync(ruta, JSON.stringify(actual, null, 2));
}
