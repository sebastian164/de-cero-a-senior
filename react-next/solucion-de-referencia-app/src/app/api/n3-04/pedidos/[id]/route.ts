import { cancelarDto } from '@talleres/react-next/nivel-03-patrones/04-capa-de-datos-y-adaptadores/api-simulada';

export async function DELETE(_: Request, { params }: { params: Promise<{ id: string }> }) {
  await new Promise((r) => setTimeout(r, 400));
  const r = cancelarDto((await params).id);
  return new Response(null, { status: r === 'ok' ? 204 : r === 'no-existe' ? 404 : 503 });
}
