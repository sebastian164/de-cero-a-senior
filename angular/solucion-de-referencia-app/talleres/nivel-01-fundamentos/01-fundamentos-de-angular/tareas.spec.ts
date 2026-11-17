// Sin TestBed ni Angular: solo las funciones puras.
import { agregarTarea, alternarTarea, contarPendientes, eliminarTarea, filtrarTareas, Tarea } from './tareas';

const base: readonly Tarea[] = [{ id: 1, titulo: 'a', hecha: true }, { id: 2, titulo: 'b', hecha: false }];

describe('tareas (funciones puras)', () => {
  it('agrega con id nuevo e ignora títulos vacíos', () => {
    expect(agregarTarea(base, '  c ')).toContainEqual({ id: 3, titulo: 'c', hecha: false });
    expect(agregarTarea(base, '   ')).toBe(base);
  });
  it('alterna y elimina sin modificar el arreglo original', () => {
    expect(alternarTarea(base, 2)[1].hecha).toBe(true);
    expect(base[1].hecha).toBe(false);
    expect(eliminarTarea(base, 1)).toEqual([base[1]]);
  });
  it('filtra y cuenta pendientes', () => {
    expect(filtrarTareas(base, 'hechas')).toEqual([base[0]]);
    expect(filtrarTareas(base, 'pendientes')).toEqual([base[1]]);
    expect(filtrarTareas(base, 'todas')).toBe(base);
    expect(contarPendientes(base)).toBe(1);
  });
});
