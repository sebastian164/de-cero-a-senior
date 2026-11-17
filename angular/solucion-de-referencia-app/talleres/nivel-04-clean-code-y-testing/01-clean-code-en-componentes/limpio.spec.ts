import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { itemsActivos, Pedido, PedidoLimpioComponent, puedeEditarPedido, totalConIva, Usuario } from './limpio';

const pedido = (extra: Partial<Pedido> = {}): Pedido => ({ id: '1', dueno: 'u1', cerrado: false, items: [
  { nombre: 'A', precio: 100, cantidad: 2, activo: true }, { nombre: 'B', precio: 50, cantidad: 1, activo: false }], ...extra });
const admin: Usuario = { id: 'u1', rol: 'admin' };

describe('n4-01 reglas puras', () => {
  it('total con IVA solo de los activos', () => expect(totalConIva(pedido().items)).toBeCloseTo(238));
  it('items activos', () => expect(itemsActivos(pedido().items)).toHaveLength(1));
  it('puede editar: admin, abierto y dueño', () => {
    expect(puedeEditarPedido(admin, pedido())).toBe(true);
    expect(puedeEditarPedido(admin, pedido({ cerrado: true }))).toBe(false);
    expect(puedeEditarPedido(admin, pedido({ dueno: 'otro' }))).toBe(false);
    expect(puedeEditarPedido({ id: 'u1', rol: 'cliente' }, pedido())).toBe(false);
    expect(puedeEditarPedido(undefined, pedido())).toBe(false);
  });
});

describe('n4-01 componente', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }));
  const montar = async (usuario?: Usuario) => {
    const f = TestBed.createComponent(PedidoLimpioComponent);
    f.componentRef.setInput('pedidoId', '1');
    if (usuario) f.componentRef.setInput('usuario', usuario);
    await f.whenStable();
    return f;
  };

  it('cargando → listo, con derivados y la condición con nombre', async () => {
    const f = await montar(admin);
    expect(f.nativeElement.querySelector('[role=status]').textContent).toContain('Cargando');
    TestBed.inject(HttpTestingController).expectOne('/api/pedidos/1').flush(pedido());
    await f.whenStable();
    const texto = f.nativeElement.textContent;
    expect(texto).toContain('Total con IVA: 238');
    expect(texto).toContain('Items activos: 1');
    expect(f.nativeElement.querySelector('button').textContent).toContain('Editar pedido');
  });

  it('emite la intención al pulsar editar', async () => {
    const f = await montar(admin);
    const pedidas: string[] = [];
    f.componentInstance.edicionSolicitada.subscribe((id) => pedidas.push(id));
    TestBed.inject(HttpTestingController).expectOne('/api/pedidos/1').flush(pedido());
    await f.whenStable();
    f.nativeElement.querySelector('button').click();
    expect(pedidas).toEqual(['1']);
  });

  it('sin permiso no hay botón; con error de la API hay un estado de error', async () => {
    const f = await montar();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/pedidos/1').flush(pedido());
    await f.whenStable();
    expect(f.nativeElement.querySelector('button')).toBeNull();
    const g = await montar();
    http.expectOne('/api/pedidos/1').flush('x', { status: 500, statusText: 'x' });
    await g.whenStable();
    expect(g.nativeElement.querySelector('[role=alert]').textContent).toContain('No se pudo cargar');
  });
});
