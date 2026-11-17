import { TestBed } from '@angular/core/testing';
import { render, screen } from '@testing-library/angular';
import { of } from 'rxjs';
import { Avatar, AvatarAntes, ResumenCompras, ResumenComprasAntes, Saludo, SaludoAntes } from './componentes';
import { ContadorPedidosComponent } from './contador.component';
import { usuarioCompleto } from './modelo';
import { ConteoDePedidos, Preferencias, proveedoresDeUsuarios, Sesion, UsuariosEnMemoria } from './servicios';

const bytes = (dato: unknown) => JSON.stringify(dato).length;

describe('Taller n2-04 — ISP', () => {
  // 1. Datos que necesita cada prueba, antes y después. Los tamaños se registran en ANALISIS.md.
  const antes = usuarioCompleto();
  const despues = { avatar: { fotoUrl: 'a.png', nombre: 'Ana' }, saludo: { nombres: 'Ana', ultimoIngreso: 'hoy' }, compras: [10, 20] };

  it('1. avatar: antes exige un Usuario completo, después dos cadenas', async () => {
    await render(AvatarAntes, { inputs: { usuario: antes } });
    expect(screen.getByRole('img', { name: 'Ana María Gómez Ruiz' })).toBeInTheDocument();
  });
  it('1. avatar después', async () => {
    await render(Avatar, { inputs: despues.avatar });
    expect(screen.getByRole('img', { name: 'Ana' })).toBeInTheDocument();
  });
  it('1. saludo antes y después muestran lo mismo', async () => {
    await render(SaludoAntes, { inputs: { usuario: antes } });
    expect(screen.getByText(/Hola, Ana María/)).toBeInTheDocument();
  });
  it('1. saludo después', async () => {
    await render(Saludo, { inputs: { datos: despues.saludo } });
    expect(screen.getByText('Hola, Ana. Último ingreso: hoy')).toBeInTheDocument();
  });
  it('1. compras antes y después', async () => {
    await render(ResumenComprasAntes, { inputs: { usuario: antes } });
    expect(screen.getByText('2 pedidos por 170000')).toBeInTheDocument();
  });
  it('1. compras después', async () => {
    await render(ResumenCompras, { inputs: { totales: despues.compras } });
    expect(screen.getByText('2 pedidos por 30')).toBeInTheDocument();
  });
  it('1. tamaño de los datos de prueba', () => {
    const tamanoAntes = 3 * bytes(antes);
    const tamanoDespues = bytes(despues.avatar) + bytes(despues.saludo) + bytes(despues.compras);
    console.info(`datos de prueba: antes ${tamanoAntes} bytes, después ${tamanoDespues} bytes`);
    expect(tamanoDespues).toBeLessThan(tamanoAntes / 5);
  });

  it('3. una sola instancia cumple todos los contratos estrechos (useExisting)', () => {
    TestBed.configureTestingModule({ providers: proveedoresDeUsuarios });
    const real = TestBed.inject(UsuariosEnMemoria);
    expect(TestBed.inject(Sesion)).toBe(real);
    expect(TestBed.inject(Preferencias)).toBe(real);
    expect(TestBed.inject(ConteoDePedidos)).toBe(real);
  });

  it('4. el contador se prueba con un doble de un solo método', async () => {
    await render(ContadorPedidosComponent, { providers: [{ provide: ConteoDePedidos, useValue: { delUsuarioActual: () => of(9) } }] });
    expect(screen.getByText('Tienes 9 pedidos')).toBeInTheDocument();
  });
});
