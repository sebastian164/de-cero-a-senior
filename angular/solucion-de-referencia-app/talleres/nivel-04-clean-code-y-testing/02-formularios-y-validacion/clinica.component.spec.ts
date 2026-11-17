import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CONFIRMAR_SALIDA } from './clinica.api';
import { ClinicaComponent, sinGuardar } from './clinica.component';
import { ClinicaReactivaComponent } from './reactivo.component';

const hoy = new Date(2026, 10, 15);
const valido = {
  nombre: 'Ana Gómez', tipoDocumento: 'cedula' as const, documento: '1234567', correo: 'ana.nueva@correo.co', clave: 'una clave larga 1', confirmacion: 'una clave larga 1',
  fechaNacimiento: '1990-05-05', acudiente: '', contactos: [{ nombre: 'Luis', telefono: '3001234567' }],
};

function montar() {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
  const f = TestBed.createComponent(ClinicaComponent);
  document.body.appendChild(f.nativeElement);       // para comprobar el foco
  f.componentInstance.hoy.set(hoy);
  return f;
}
afterEach(() => document.body.replaceChildren());

describe('n4-02 formulario de la clínica', () => {
  it('el acudiente solo existe y es obligatorio para menores', async () => {
    const f = montar();
    const c = f.componentInstance;
    await f.whenStable();
    expect(f.nativeElement.querySelector('#acudiente')).toBeNull();
    c.modelo.update((m) => ({ ...m, fechaNacimiento: '2015-01-01' }));
    await f.whenStable();
    expect(f.nativeElement.querySelector('#acudiente')).not.toBeNull();
    expect(c.formulario.acudiente().errors().map((e) => e.message)).toContain('Un menor de edad necesita un acudiente');
    c.modelo.update((m) => ({ ...m, acudiente: 'Marta' }));
    await f.whenStable();
    expect(c.formulario.acudiente().valid()).toBe(true);
    c.modelo.update((m) => ({ ...m, fechaNacimiento: '1990-01-01' }));
    await f.whenStable();
    expect(f.nativeElement.querySelector('#acudiente')).toBeNull();
  });

  it('contactos: mínimo uno, máximo tres', async () => {
    const f = montar();
    await f.whenStable();
    const botones = () => [...f.nativeElement.querySelectorAll('button')] as HTMLButtonElement[];
    const agregar = () => botones().find((b) => b.textContent === 'Agregar contacto')!;
    expect(botones().find((b) => b.textContent?.includes('Quitar contacto 1'))!.disabled).toBe(true);
    agregar().click(); await f.whenStable();
    agregar().click(); await f.whenStable();
    expect(f.nativeElement.querySelectorAll('fieldset')).toHaveLength(3);
    expect(agregar().disabled).toBe(true);
  });

  it('enviar inválido no llama al servidor y deja el foco en el primer error', async () => {
    const f = montar();
    await f.whenStable();
    f.nativeElement.querySelector('button[type=submit]').click();
    await f.whenStable();
    TestBed.inject(HttpTestingController).expectNone('/api/usuarios');
    expect(document.activeElement?.id).toBe('nombre');
    expect(f.nativeElement.querySelector('#nombre-error').textContent).toContain('Escribe tu nombre');
    expect(f.nativeElement.querySelector('#nombre').getAttribute('aria-invalid')).toBe('true');
  });

  it('un 422 del servidor se asigna al campo del correo y conserva lo escrito', async () => {
    const f = montar();
    const c = f.componentInstance;
    c.modelo.set({ ...valido, correo: 'tomado@correo.co' });
    // La comprobación asíncrona espera su debounce (400 ms); sin whenStable() mientras la petición está pendiente.
    const envio = (c as unknown as { enviar(e: Event): Promise<void> }).enviar(new Event('submit'));
    await new Promise((r) => setTimeout(r, 600));
    const http = TestBed.inject(HttpTestingController);
    for (const p of http.match((r) => r.url.startsWith('/api/usuarios/existe'))) p.flush({ existe: false });
    await new Promise((r) => setTimeout(r, 0));
    const post = http.expectOne({ method: 'POST', url: '/api/usuarios' });
    post.flush({ campo: 'correo', mensaje: 'El servidor rechazó este correo' }, { status: 422, statusText: 'x' });
    await envio;
    await f.whenStable();
    expect(c.formulario.correo().errors().map((e) => e.message)).toContain('El servidor rechazó este correo');
    expect(c.modelo().nombre).toBe('Ana Gómez');
  });

  it('un 422 que señala otro campo (documento) cae en ese campo, no en el correo', async () => {
    const f = montar();
    const c = f.componentInstance;
    c.modelo.set(valido);
    const envio = (c as unknown as { enviar(e: Event): Promise<void> }).enviar(new Event('submit'));
    await new Promise((r) => setTimeout(r, 600));
    const http = TestBed.inject(HttpTestingController);
    for (const p of http.match((r) => r.url.startsWith('/api/usuarios/existe'))) p.flush({ existe: false });
    await new Promise((r) => setTimeout(r, 0));
    http.expectOne({ method: 'POST', url: '/api/usuarios' }).flush({ campo: 'documento', mensaje: 'Documento ya registrado' }, { status: 422, statusText: 'x' });
    await envio;
    await f.whenStable();
    expect(c.formulario.documento().errors().map((e) => e.message)).toContain('Documento ya registrado');
    expect(c.formulario.correo().errors()).toEqual([]);
    expect(document.activeElement?.id).toBe('documento');
  });

  it('un 500 muestra un mensaje general y conserva los datos; se puede reintentar', async () => {
    const f = montar();
    const c = f.componentInstance;
    c.modelo.set(valido);
    const envio = (c as unknown as { enviar(e: Event): Promise<void> }).enviar(new Event('submit'));
    await new Promise((r) => setTimeout(r, 600));
    const http = TestBed.inject(HttpTestingController);
    for (const p of http.match((r) => r.url.startsWith('/api/usuarios/existe'))) p.flush({ existe: false });
    await new Promise((r) => setTimeout(r, 0));
    http.expectOne({ method: 'POST', url: '/api/usuarios' }).flush('x', { status: 500, statusText: 'x' });
    await envio; await f.whenStable();
    expect(f.nativeElement.textContent).toContain('No pudimos registrarte. Tus datos siguen aquí');
    expect(c.modelo().correo).toBe(valido.correo);
    expect(c.registrado()).toBe(false);
  });

  it('si el servicio de comprobación del correo falla, no bloquea', async () => {
    const f = montar();
    const c = f.componentInstance;
    c.modelo.set({ ...valido, correo: 'caido@correo.co' });
    await new Promise((r) => setTimeout(r, 600));
    TestBed.inject(HttpTestingController).match((r) => r.url.startsWith('/api/usuarios/existe')).forEach((p) => p.flush('x', { status: 503, statusText: 'x' }));
    await new Promise((r) => setTimeout(r, 0)); await f.whenStable();
    expect(c.formulario.correo().invalid()).toBe(false);
  });
});

describe('n4-02 guarda de cambios sin guardar', () => {
  it('pide confirmación solo si hay cambios', async () => {
    const preguntas: string[] = [];
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: CONFIRMAR_SALIDA, useValue: (m: string) => { preguntas.push(m); return false; } }] });
    const f = TestBed.createComponent(ClinicaComponent);
    await f.whenStable();
    const salir = () => TestBed.runInInjectionContext(() => sinGuardar(f.componentInstance, null as never, null as never, null as never));
    expect(salir()).toBe(true);                       // sin cambios: sale
    expect(preguntas).toHaveLength(0);
    f.nativeElement.querySelector('#nombre').value = 'A';
    f.nativeElement.querySelector('#nombre').dispatchEvent(new Event('input'));
    await f.whenStable();
    expect(salir()).toBe(false);                      // con cambios: pregunta y el doble responde "no"
    expect(preguntas[0]).toContain('cambios sin guardar');
  });
});

describe('n4-02 versión reactiva', () => {
  it('reglas de grupo reutilizan las funciones puras', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    const f = TestBed.createComponent(ClinicaReactivaComponent);
    const g = f.componentInstance.formulario;
    f.componentInstance.hoy.set(hoy);
    g.patchValue({ clave: 'una clave larga 1', confirmacion: 'otra' });
    expect(g.errors).toMatchObject({ clavesDistintas: 'Las claves no coinciden' });
    g.patchValue({ confirmacion: 'una clave larga 1', fechaNacimiento: '2015-01-01', tipoDocumento: 'cedula', documento: '1234567' });
    expect(g.errors).toMatchObject({ acudiente: 'Un menor de edad necesita un acudiente' });
    g.patchValue({ acudiente: 'Marta' });
    expect(g.errors).toBeNull();
  });
});
