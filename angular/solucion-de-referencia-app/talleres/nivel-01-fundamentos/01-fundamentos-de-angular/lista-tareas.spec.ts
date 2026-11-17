import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import rutas from './rutas';
import { ListaTareasComponent } from './lista-tareas.component';

describe('Lista de tareas', () => {
  it('agrega, marca, filtra, elimina y el contador de pendientes se deriva solo', async () => {
    await render(ListaTareasComponent, { providers: [provideRouter([])] });
    const u = userEvent.setup();
    expect(screen.getByRole('status')).toHaveTextContent('Pendientes: 1');
    await u.type(screen.getByLabelText('Nueva tarea'), 'Comprar pan');
    await u.click(screen.getByRole('button', { name: 'Agregar' }));
    expect(screen.getByRole('status')).toHaveTextContent('Pendientes: 2');
    await u.click(screen.getByRole('checkbox', { name: 'Comprar pan' }));
    expect(screen.getByRole('status')).toHaveTextContent('Pendientes: 1');
    await u.click(screen.getByRole('button', { name: 'hechas' }));
    expect(screen.getAllByRole('checkbox')).toHaveLength(2);
    await u.click(screen.getByRole('button', { name: 'pendientes' }));
    expect(screen.getAllByRole('checkbox')).toHaveLength(1);
    await u.click(screen.getByRole('button', { name: 'Eliminar Hacer el taller' }));
    expect(screen.getByText('No hay tareas.')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Pendientes: 0');
  });

  it('la ruta de detalle recibe el id como entrada', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter(rutas, withComponentInputBinding())] });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/1');
    expect(harness.routeNativeElement?.textContent).toContain('Leer la sesión 01');
  });
});

