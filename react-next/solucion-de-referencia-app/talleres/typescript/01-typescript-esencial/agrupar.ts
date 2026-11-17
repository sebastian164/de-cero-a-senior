// Punto 3: agruparPor con tipos correctos. La clave del mapa conserva el tipo de la propiedad.
export function agruparPor<T, K extends keyof T>(lista: readonly T[], clave: K): Map<T[K], T[]> {
  const grupos = new Map<T[K], T[]>();
  for (const elemento of lista) {
    const valor = elemento[clave];
    const grupo = grupos.get(valor);
    if (grupo) grupo.push(elemento);
    else grupos.set(valor, [elemento]);
  }
  return grupos;
}
