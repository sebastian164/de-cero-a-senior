import { connection } from 'next/server';

// Las páginas de esta sección llevan una CSP con nonce por petición (src/proxy.ts): el nonce exige renderizar en cada petición.
export const instant = false;

export default async function Diseno({ children }: { children: React.ReactNode }) {
  await connection();
  return children;
}
