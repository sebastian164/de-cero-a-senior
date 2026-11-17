// Taller TS 02 — punto 1: Curso con cupo máximo en dos versiones.

// Versión A: clase con campos privados de JavaScript.
export class Curso {
  readonly #inscritos: string[] = [];

  constructor(readonly nombre: string, readonly cupo: number) {
    if (!Number.isInteger(cupo) || cupo <= 0) throw new Error('El cupo debe ser un entero positivo');
  }

  inscribir(estudiante: string): void {
    if (this.#inscritos.includes(estudiante)) throw new Error(`${estudiante} ya está inscrito`);
    if (this.lleno) throw new Error('El curso está lleno');
    this.#inscritos.push(estudiante);
  }

  retirar(estudiante: string): void {
    const i = this.#inscritos.indexOf(estudiante);
    if (i === -1) throw new Error(`${estudiante} no está inscrito`);
    this.#inscritos.splice(i, 1);
  }

  get lleno(): boolean {
    return this.#inscritos.length >= this.cupo;
  }

  get inscritos(): readonly string[] {
    return [...this.#inscritos];
  }
}

// Versión B: estado inmutable y funciones puras.
export interface EstadoCurso {
  readonly nombre: string;
  readonly cupo: number;
  readonly inscritos: readonly string[];
}

export type Resultado = { ok: true; curso: EstadoCurso } | { ok: false; error: string };

export const crearCurso = (nombre: string, cupo: number): EstadoCurso => {
  if (!Number.isInteger(cupo) || cupo <= 0) throw new Error('El cupo debe ser un entero positivo');
  return { nombre, cupo, inscritos: [] };
};

export const estaLleno = (curso: EstadoCurso) => curso.inscritos.length >= curso.cupo;

export function inscribir(curso: EstadoCurso, estudiante: string): Resultado {
  if (curso.inscritos.includes(estudiante)) return { ok: false, error: `${estudiante} ya está inscrito` };
  if (estaLleno(curso)) return { ok: false, error: 'El curso está lleno' };
  return { ok: true, curso: { ...curso, inscritos: [...curso.inscritos, estudiante] } };
}

export function retirar(curso: EstadoCurso, estudiante: string): Resultado {
  if (!curso.inscritos.includes(estudiante)) return { ok: false, error: `${estudiante} no está inscrito` };
  return { ok: true, curso: { ...curso, inscritos: curso.inscritos.filter((e) => e !== estudiante) } };
}
