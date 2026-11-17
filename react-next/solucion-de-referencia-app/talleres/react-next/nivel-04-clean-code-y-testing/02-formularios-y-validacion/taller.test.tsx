import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { EsquemaPaciente, desdeFormData, erroresPorCampo } from './esquema';
import { registrarPaciente, registrarPacienteObjeto } from './acciones';
import { cantidadPacientes } from './pacientes';
import { FormularioClinicaBiblioteca } from './formulario-biblioteca';

const valido = {
  nombre: 'Ana Gómez', documento: '52123456', nacimiento: '1990-05-10', correo: 'ana@correo.example', telefono: '3001234567',
  contactos: [{ nombre: 'Luis Gómez', telefono: '3017654321', parentesco: 'padre' as const }], aceptaDatos: true as const,
};

describe('nivel-04/02 · punto 7: el esquema, sin React', () => {
  it('acepta un registro válido', () => {
    expect(EsquemaPaciente.safeParse(valido).success).toBe(true);
  });
  it.each([
    [{ documento: '12' }, 'documento', 'Entre 6 y 10 dígitos'],
    [{ correo: 'x' }, 'correo', 'Escribe un correo válido'],
    [{ contactos: [] }, 'contactos', 'Agrega al menos un contacto de emergencia'],
    [{ contactos: Array(4).fill(valido.contactos[0]) }, 'contactos', 'Máximo 3 contactos'],
    [{ aceptaDatos: false }, 'aceptaDatos', 'Debes autorizar el tratamiento de datos'],
    [{ nacimiento: '2999-01-01' }, 'nacimiento', 'La fecha no puede ser futura'],
  ])('rechaza %o en %s', (cambio, campo, mensaje) => {
    const r = EsquemaPaciente.safeParse({ ...valido, ...cambio });
    expect(r.success).toBe(false);
    if (!r.success) expect(erroresPorCampo(r.error)[campo]).toContain(mensaje);
  });
  it('validaciones entre campos: menor sin acudiente y contacto con el mismo teléfono', () => {
    const menor = EsquemaPaciente.safeParse({ ...valido, nacimiento: '2015-01-01' });
    expect(!menor.success && erroresPorCampo(menor.error).acudiente).toEqual(['Los menores de edad necesitan un acudiente']);
    expect(EsquemaPaciente.safeParse({ ...valido, nacimiento: '2015-01-01', acudiente: 'Marta Ríos' }).success).toBe(true);
    const mismo = EsquemaPaciente.safeParse({ ...valido, contactos: [{ ...valido.contactos[0], telefono: valido.telefono }] });
    expect(!mismo.success && erroresPorCampo(mismo.error).contactos).toEqual(['El contacto de emergencia no puede tener tu mismo teléfono']);
  });
  it('desdeFormData arma los contactos desde nombres con punto', () => {
    const f = new FormData();
    f.set('nombre', 'Ana'); f.set('contactos.1.nombre', 'B'); f.set('contactos.0.nombre', 'A'); f.set('aceptaDatos', 'on');
    expect(desdeFormData(f)).toEqual({ nombre: 'Ana', aceptaDatos: true, contactos: [{ nombre: 'A' }, { nombre: 'B' }] });
  });
});

describe('nivel-04/02 · punto 6: la acción rechaza datos inválidos aunque la llamen directamente', () => {
  it('FormData inválido fuera del formulario: errores y nada guardado', async () => {
    const antes = cantidadPacientes();
    const f = new FormData();
    f.set('nombre', 'X'); f.set('documento', 'abc'); f.set('contactos.0.telefono', '123');
    const r = await registrarPaciente({}, f);
    expect(r.errores?.documento).toEqual(['Entre 6 y 10 dígitos']);
    expect(r.errores?.['contactos.0.telefono']).toEqual(['Celular de 10 dígitos que empiece por 3']);
    expect(cantidadPacientes()).toBe(antes);
  });
  it('objeto manipulado (aceptaDatos ausente, contactos de más) también se rechaza', async () => {
    const r = await registrarPacienteObjeto({ ...valido, aceptaDatos: undefined as unknown as true, contactos: Array(5).fill(valido.contactos[0]) });
    expect(r.errores?.contactos).toEqual(['Máximo 3 contactos']);
    expect(r.errores?.aceptaDatos).toBeDefined();
  });
  it('sin JavaScript, "Agregar contacto" vuelve con una fila más y conserva lo escrito', async () => {
    const f = new FormData();
    f.set('nombre', 'Ana'); f.set('intencion', 'agregar-contacto');
    expect(await registrarPaciente({ contactos: 1 }, f)).toEqual({ valores: { nombre: 'Ana', intencion: 'agregar-contacto', aceptaDatos: false, contactos: [] }, contactos: 2 });
  });
});

describe('nivel-04/02 · puntos 3, 4 y 5: formulario con biblioteca', () => {
  it('valida al salir del campo', async () => {
    const u = userEvent.setup();
    render(<FormularioClinicaBiblioteca />);
    await u.type(screen.getByLabelText('Correo'), 'no-es-correo');
    await u.tab();
    expect(await screen.findByText('Escribe un correo válido')).toBeInTheDocument();
    expect(screen.getByLabelText('Correo')).toHaveAttribute('aria-invalid', 'true');
  });
  it('contactos: mínimo uno y máximo tres', async () => {
    const u = userEvent.setup();
    render(<FormularioClinicaBiblioteca />);
    expect(screen.getByRole('button', { name: 'Quitar contacto 1' })).toBeDisabled();
    await u.click(screen.getByRole('button', { name: 'Agregar contacto' }));
    await u.click(screen.getByRole('button', { name: 'Agregar contacto' }));
    expect(screen.getByLabelText('Contacto 3: nombre')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Agregar contacto' })).toBeDisabled();
    await u.click(screen.getByRole('button', { name: 'Quitar contacto 2' }));
    expect(screen.queryByLabelText('Contacto 3: nombre')).not.toBeInTheDocument();
  });
  it('el error del servidor queda en su campo y recibe el foco', async () => {
    const u = userEvent.setup();
    const enviar = vi.fn(async () => ({ errores: { documento: ['Ya hay un paciente con ese documento'] } }));
    render(<FormularioClinicaBiblioteca enviar={enviar} />);
    await u.type(screen.getByLabelText('Nombre completo'), valido.nombre);
    await u.type(screen.getByLabelText('Documento'), valido.documento);
    await u.type(screen.getByLabelText('Fecha de nacimiento'), valido.nacimiento);
    await u.type(screen.getByLabelText('Correo'), valido.correo);
    await u.type(screen.getByLabelText('Celular'), valido.telefono);
    await u.type(screen.getByLabelText('Contacto 1: nombre'), 'Luis Gómez');
    await u.type(screen.getByLabelText('Contacto 1: celular'), '3017654321');
    await u.click(screen.getByLabelText('Autorizo el tratamiento de mis datos'));
    await u.click(screen.getByRole('button', { name: 'Registrar paciente' }));
    await waitFor(() => expect(enviar).toHaveBeenCalledOnce());
    expect(await screen.findByText('Ya hay un paciente con ese documento')).toBeInTheDocument();
    expect(screen.getByLabelText('Documento')).toHaveFocus();
  });
  it('al enviar vacío, el foco va al primer campo con error', async () => {
    render(<FormularioClinicaBiblioteca />);
    await userEvent.click(screen.getByRole('button', { name: 'Registrar paciente' }));
    await waitFor(() => expect(screen.getByLabelText('Nombre completo')).toHaveFocus());
  });
});
