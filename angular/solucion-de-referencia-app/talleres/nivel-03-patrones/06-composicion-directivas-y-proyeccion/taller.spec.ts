import { Component, signal } from '@angular/core';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { Subject } from 'rxjs';
import { Datos } from './antes';
import { Clientes, Resumen } from './despues';
import { AutofocoDirective, ClicFueraDirective } from './directivas';
import { PaginaComposicion } from './pagina.component';

@Component({
  imports: [AutofocoDirective, ClicFueraDirective],
  template: `<input aria-label="campo" appAutofoco /><div data-testid="zona" (appClicFuera)="fuera.set(fuera() + 1)"><button>dentro</button></div><button>afuera</button>`,
})
class Anfitrion { readonly fuera = signal(0); }

describe('Taller n3-06', () => {
  it('1. autofoco y clic fuera', async () => {
    const { fixture } = await render(Anfitrion);
    expect(screen.getByLabelText('campo')).toHaveFocus();
    await userEvent.click(screen.getByText('dentro'));
    expect(fixture.componentInstance.fuera()).toBe(0);
    await userEvent.click(screen.getByText('afuera'));
    expect(fixture.componentInstance.fuera()).toBe(1);
  });

  it('2 y 5. diálogo: proyección en sus huecos, foco en Cerrar, clic fuera por hostDirectives (salida "fuera")', async () => {
    await render(PaginaComposicion);
    await userEvent.click(screen.getByRole('button', { name: 'Eliminar cuenta' }));
    const dialogo = screen.getByRole('dialog', { name: '¿Eliminar la cuenta?' });
    expect(dialogo.querySelector('header')).toHaveTextContent('¿Eliminar la cuenta?');
    expect(dialogo.querySelector('.cuerpo')).toHaveTextContent('Esta acción no se puede deshacer.');
    expect(dialogo.querySelector('footer')).toHaveTextContent('Sí, eliminar');
    expect(screen.getByRole('button', { name: 'Cerrar' })).toHaveFocus();
    await userEvent.click(screen.getByRole('heading', { name: 'Composición' }));      // clic fuera
    expect(screen.queryByRole('dialog')).toBeNull();
    await userEvent.click(screen.getByRole('button', { name: 'Eliminar cuenta' }));
    await userEvent.click(screen.getByRole('button', { name: 'Sí, eliminar' }));
    expect(screen.getByRole('status')).toHaveTextContent('Cuenta eliminada');
  });

  it('3. la lista con estados sirve a pedidos y a usuarios', async () => {
    await render(PaginaComposicion);
    expect(screen.getByText('No hay pedidos todavía.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ana' })).toHaveAttribute('href', 'mailto:ana@x.co');
    await userEvent.click(screen.getByRole('button', { name: 'Cargar pedidos' }));
    expect(screen.getByText('Buscando pedidos…')).toBeInTheDocument();     // plantilla de carga propia
    expect(await screen.findByText(/Luis — \$\s80\.000/)).toBeInTheDocument();
  });

  it('4. sin clase base: usarCarga limpia al destruir y la directiva resalta', async () => {
    const fuente = new Subject<string[]>();
    const { fixture } = await render(Clientes, { providers: [{ provide: Datos, useValue: { clientes: () => fuente } }] });
    expect(fixture.nativeElement.textContent).toContain('Cargando clientes…');
    fuente.next(['Ana']);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Clientes: Ana');
    fixture.destroy();
    expect(fuente.observed).toBe(false);                                     // takeUntilDestroyed
  });

  it('4. el resumen toma solo el resaltado, por hostDirectives', async () => {
    const r = await render(Resumen);
    r.fixture.nativeElement.dispatchEvent(new MouseEvent('mouseenter'));
    await r.fixture.whenStable();
    expect(r.fixture.nativeElement).toHaveClass('resaltado');
  });

});
