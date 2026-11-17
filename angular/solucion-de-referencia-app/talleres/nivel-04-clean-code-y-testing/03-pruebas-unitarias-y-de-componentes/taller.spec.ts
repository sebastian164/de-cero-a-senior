import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { of, Subject } from 'rxjs';
import { AlmacenPedidos, PedidosApi, total, unPedido } from '../../../proyectos/ejemplos/nivel-04-clean-code-y-testing/03-pruebas-unitarias-y-de-componentes/pedidos';
import { BuscadorComponent } from './buscador.component';
import { ContactoComponent, validarConsulta } from './contacto.component';

// 1. Funciones puras del dominio
describe('1. dominio puro', () => {
  it('total sin líneas es 0', () => expect(total(unPedido())).toBe(0));
  it('total aplica el IVA del 19 %', () => expect(total(unPedido({ lineas: [{ precio: 100, cantidad: 1 }] }))).toBeCloseTo(119));
  it('validarConsulta: correo y mensaje', () => {
    expect(validarConsulta({ correo: 'a@b.co', mensaje: 'mensaje largo ok' })).toEqual({});
    expect(validarConsulta({ correo: 'x', mensaje: 'corto' })).toEqual({ correo: 'Escribe un correo válido', mensaje: 'El mensaje necesita al menos 10 caracteres' });
  });
});

// 2. Almacén: carga, filtro, actualización optimista y su reversa
describe('2. AlmacenPedidos', () => {
  const montar = (api: Partial<PedidosApi>) => { TestBed.configureTestingModule({ providers: [{ provide: PedidosApi, useValue: api }] }); return TestBed.inject(AlmacenPedidos); };
  it('carga y filtra', () => {
    const a = montar({ listar: () => of([unPedido({ id: 'a', estado: 'pendiente' }), unPedido({ id: 'b', estado: 'pagado' })]) });
    a.cargar();
    expect(a.visibles()).toHaveLength(2);
    a.filtrar('pagado');
    expect(a.visibles().map((p) => p.id)).toEqual(['b']);
  });
  it('cancelar es optimista y se revierte si la API falla', () => {
    const respuesta = new Subject<void>();
    const a = montar({ listar: () => of([unPedido({ id: 'a' })]), cancelar: () => respuesta });
    a.cargar();
    a.cancelar('a');
    expect(a.visibles()).toHaveLength(0);
    respuesta.error(new Error('409'));
    expect(a.visibles()).toHaveLength(1);
    expect(a.error()).toBe('No se pudo cancelar');
  });
  it('cancelar con éxito se mantiene', () => {
    const respuesta = new Subject<void>();
    const a = montar({ listar: () => of([unPedido({ id: 'a' })]), cancelar: () => respuesta });
    a.cargar(); a.cancelar('a'); respuesta.next(); respuesta.complete();
    expect(a.visibles()).toHaveLength(0);
    expect(a.error()).toBe('');
  });
});

// 3. Servicio HTTP, incluido el 500
describe('3. PedidosApi', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }));
  it('traduce estados y centavos', () => {
    const api = TestBed.inject(PedidosApi); const http = TestBed.inject(HttpTestingController);
    let r: unknown[] = [];
    api.listar().subscribe((p) => (r = p));
    http.expectOne('/api/pedidos').flush([{ id: 7, estado: 1, valor_centavos: 12000 }, { id: 8, estado: 2, valor_centavos: 500 }]);
    expect(r).toMatchObject([{ id: '7', estado: 'pendiente', total: 120 }, { id: '8', estado: 'pagado', total: 5 }]);
    http.verify();
  });
  it('un 500 llega como error al suscriptor y el almacén lo comunica', () => {
    const almacen = TestBed.inject(AlmacenPedidos); const http = TestBed.inject(HttpTestingController);
    almacen.cargar();
    http.expectOne('/api/pedidos').flush('boom', { status: 500, statusText: 'Server Error' });
    expect(almacen.error()).toBe('No se pudieron cargar los pedidos');
    expect(almacen.visibles()).toEqual([]);
  });
});

// 4. Formulario como usuario
describe('4. ContactoComponent como usuario', () => {
  it('muestra los errores al enviar vacío y no emite', async () => {
    const consultaEnviada = vi.fn();
    await render(ContactoComponent, { on: { consultaEnviada } });
    await userEvent.click(screen.getByRole('button', { name: 'Enviar consulta' }));
    expect(screen.getAllByRole('alert').map((a) => a.textContent)).toEqual(['Escribe un correo válido', 'El mensaje necesita al menos 10 caracteres']);
    expect(consultaEnviada).not.toHaveBeenCalled();
  });
  it('llena, envía y ve la confirmación', async () => {
    const consultaEnviada = vi.fn();
    await render(ContactoComponent, { on: { consultaEnviada } });
    await userEvent.type(screen.getByLabelText('Correo'), 'ana@correo.co');
    await userEvent.type(screen.getByLabelText('Mensaje'), 'Quisiera una cita');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar consulta' }));
    expect(screen.getByRole('status')).toHaveTextContent('Consulta enviada');
    expect(consultaEnviada).toHaveBeenCalledWith({ correo: 'ana@correo.co', mensaje: 'Quisiera una cita' });
  });
});

// 5. debounceTime con tiempo simulado
describe('5. BuscadorComponent con tiempo simulado', () => {
  beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: false }));
  afterEach(() => vi.useRealTimers());
  it('emite una sola vez, 300 ms después de la última tecla', async () => {
    const buscar = vi.fn();
    const user = userEvent.setup({ advanceTimers: (ms) => vi.advanceTimersByTime(ms) });
    await render(BuscadorComponent, { on: { buscar } });
    await user.type(screen.getByRole('searchbox'), 'ana');
    expect(buscar).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(299);
    expect(buscar).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);
    expect(buscar).toHaveBeenCalledTimes(1);
    expect(buscar).toHaveBeenCalledWith('ana');
  });
});
