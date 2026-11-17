import { TestBed } from '@angular/core/testing';
import { firstValueFrom, Observable, of } from 'rxjs';
import { CancelarPedido, RELOJ } from './aplicacion/cancelar-pedido';
import { aPedido } from './datos/pedidos-http.repositorio';
import { Pedido, PedidoNoCancelable, sePuedeCancelar } from './dominio/pedido';
import { RepositorioPedidos } from './dominio/puertos';

const hora = (h: number) => new Date(Date.UTC(2026, 10, 15, h));
const pedido = (extra: Partial<Pedido> = {}): Pedido => ({ id: '1', estado: 'pendiente', creadoEn: hora(0), lineas: [], ...extra });

describe('n5-03 ejemplo: la regla de las 24 horas, sin Angular, sin HTTP y con el reloj que se quiera', () => {
  it.each([
    ['pendiente', 0, true], ['pagado', 24, true], ['pendiente', 25, false], ['enviado', 1, false], ['cancelado', 1, false],
  ] as const)('%s a las %i horas → %s', (estado, h, esperado) => {
    expect(sePuedeCancelar(pedido({ estado }), new Date(hora(0).getTime() + h * 3_600_000))).toBe(esperado);
  });
});

describe('n5-03 ejemplo: caso de uso con un repositorio falso y un reloj inyectado', () => {
  const montar = (cancelar: (id: string) => Observable<void>, ahora: number) => {
    TestBed.configureTestingModule({ providers: [CancelarPedido, { provide: RepositorioPedidos, useValue: { cancelar } }, { provide: RELOJ, useValue: () => ahora }] });
    return TestBed.inject(CancelarPedido);
  };
  it('cancela dentro de las 24 horas y llama al repositorio', async () => {
    const cancelar = vi.fn(() => of(undefined));
    await firstValueFrom(montar(cancelar, hora(10).getTime()).ejecutar(pedido()), { defaultValue: undefined });
    expect(cancelar).toHaveBeenCalledWith('1');
  });
  it('fuera de plazo falla con PedidoNoCancelable y no llama al repositorio', async () => {
    const cancelar = vi.fn(() => of(undefined));
    await expect(firstValueFrom(montar(cancelar, hora(0).getTime() + 30 * 3_600_000).ejecutar(pedido()))).rejects.toBeInstanceOf(PedidoNoCancelable);
    expect(cancelar).not.toHaveBeenCalled();
  });
});

describe('n5-03 ejemplo: el adaptador traduce el DTO', () => {
  it('aPedido', () => {
    expect(aPedido({ id: 7, fecha_creacion: '2026-11-15T00:00:00Z', estado: 2, lineas: [{ producto_id: 'x', cantidad: 3 }] }))
      .toEqual({ id: '7', estado: 'pagado', creadoEn: new Date('2026-11-15T00:00:00Z'), lineas: [{ productoId: 'x', cantidad: 3 }] });
  });
});
