// Punto 4: módulo de composición. Es el único archivo que conoce las implementaciones reales.
import 'server-only';
import { crearCancelarPedido, repositorioEnMemoria } from './casos-de-uso';

const hace = (horas: number) => new Date(Date.now() - horas * 3_600_000);

export const repositorio = repositorioEnMemoria([
  { id: 'A-1', clienteId: 'u-1', creadoEn: hace(2), estado: 'pendiente' },
  { id: 'A-2', clienteId: 'u-1', creadoEn: hace(30), estado: 'pendiente' },
  { id: 'B-1', clienteId: 'u-2', creadoEn: hace(1), estado: 'pendiente' },
]);

export const cancelarPedido = crearCancelarPedido(repositorio, () => new Date());
