import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { AlmacenPedidos, Pedido, PedidosApi } from './almacen-pedidos';

const p = (id: string, estado: Pedido['estado'], total = 10): Pedido => ({ id, cliente: id, estado, total });

describe('n3-03 AlmacenPedidos (ejemplo)', () => {
  it('carga, filtra y deriva el total pendiente', () => {
    TestBed.configureTestingModule({ providers: [{ provide: PedidosApi, useValue: { listar: () => of([p('a', 'pendiente', 5), p('b', 'pagado'), p('c', 'pendiente', 7)]) } }] });
    const a = TestBed.inject(AlmacenPedidos);
    a.cargar();
    expect(a.totalPendiente()).toBe(12);
    a.filtrar('pagado');
    expect(a.visibles().map((x) => x.id)).toEqual(['b']);
  });
  it('cancelación optimista con reversa si la API falla', () => {
    const respuesta = new Subject<void>();
    TestBed.configureTestingModule({ providers: [{ provide: PedidosApi, useValue: { listar: () => of([p('a', 'pendiente')]), cancelar: () => respuesta } }] });
    const a = TestBed.inject(AlmacenPedidos);
    a.cargar();
    a.cancelar('a');
    expect(a.visibles()).toHaveLength(0);          // optimista
    respuesta.error(new Error('409'));
    expect(a.visibles()).toHaveLength(1);          // reversa
    expect(a.error()).toBe('No se pudo cancelar');
  });
  it('error al cargar', () => {
    TestBed.configureTestingModule({ providers: [{ provide: PedidosApi, useValue: { listar: () => throwError(() => new Error('x')) } }] });
    const a = TestBed.inject(AlmacenPedidos);
    a.cargar();
    expect(a.error()).toBe('No se pudieron cargar los pedidos');
    expect(a.cargando()).toBe(false);
  });
});
