import { bd } from '@/react-next/n6-02-seguridad/bd';
import { reiniciarLimiteDeIngreso } from '@talleres/react-next/nivel-06-avanzado/02-seguridad-y-autenticacion/limite';

// Solo para las pruebas de esta aplicación: restaura los pedidos de la semilla y vacía el límite de ingreso.
export async function POST() { bd.reiniciar(); reiniciarLimiteDeIngreso(); return Response.json({ ok: true }); }
