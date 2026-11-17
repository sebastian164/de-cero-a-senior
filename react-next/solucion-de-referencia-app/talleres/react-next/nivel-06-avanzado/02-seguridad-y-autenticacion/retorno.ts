// Redirecciones abiertas: solo se acepta volver a una ruta propia y relativa.
export function retornoSeguro(valor: unknown, porDefecto = '/n6/02-seguridad/privado'): string {
  if (typeof valor !== 'string' || !valor.startsWith('/') || valor.startsWith('//') || valor.includes('\\')) return porDefecto;
  try {
    const url = new URL(valor, 'http://local.invalid');
    return url.origin === 'http://local.invalid' ? url.pathname + url.search : porDefecto;
  } catch {
    return porDefecto;
  }
}
