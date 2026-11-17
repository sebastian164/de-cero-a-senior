// 2. Dos componentes en lugares distintos reflejan los mismos datos.
import { Component } from '@angular/core';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { of } from 'rxjs';
import { CarritoApi } from './almacen-carrito';
import { IconoCarrito, PaginaCarrito } from './componentes';

@Component({ imports: [IconoCarrito, PaginaCarrito], template: `<aside><app-icono-carrito /></aside><app-pagina-carrito />` })
class Pantalla {}

it('el ícono de otra zona y la página muestran la misma cantidad', async () => {
  await render(Pantalla, { providers: [provideRouter([]), { provide: CarritoApi, useValue: { guardar: () => of({}) } }] });
  await userEvent.click(screen.getByRole('button', { name: 'Agregar Teclado' }));
  await userEvent.click(screen.getByRole('button', { name: 'Agregar Mouse' }));
  expect(screen.getAllByTestId('contador').map((e) => e.textContent)).toEqual(['2', '2']);
  expect(screen.getByRole('heading', { name: 'Tu carrito (2)' })).toBeInTheDocument();
});
