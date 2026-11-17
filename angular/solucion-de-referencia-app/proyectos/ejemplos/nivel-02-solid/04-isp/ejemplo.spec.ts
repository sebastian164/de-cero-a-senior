import { TestBed } from '@angular/core/testing';
import { render, screen } from '@testing-library/angular';
import { of } from 'rxjs';
import { AvatarComponent, ConteoDePedidos, ContadorPedidosComponent, PedidosApi, proveedoresIsp } from './isp';
import { provideHttpClient } from '@angular/common/http';

describe('n2-04 ISP (después)', () => {
  it('el avatar solo necesita dos cadenas', async () => {
    await render(AvatarComponent, { inputs: { fotoUrl: 'a.png', nombre: 'Ana' } });
    expect(screen.getByRole('img', { name: 'Ana' })).toHaveAttribute('src', 'a.png');
  });
  it('el contador se prueba con un doble de un solo método', async () => {
    await render(ContadorPedidosComponent, { providers: [{ provide: ConteoDePedidos, useValue: { delUsuarioActual: () => of(5) } }] });
    expect(screen.getByText('Tienes 5 pedidos')).toBeInTheDocument();
  });
  it('useExisting: el contrato estrecho y la clase completa son la misma instancia', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), ...proveedoresIsp] });
    expect(TestBed.inject(ConteoDePedidos)).toBe(TestBed.inject(PedidosApi));
  });
});
