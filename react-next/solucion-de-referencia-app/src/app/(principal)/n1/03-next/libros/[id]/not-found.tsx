import Link from 'next/link';

export default function LibroNoEncontrado() {
  return <main><h1>Ese libro no existe</h1><Link href="/n1/03-next/libros">Volver a la lista</Link></main>;
}
