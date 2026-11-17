'use client';

export default function ErrorLibro({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <main role="alert">
      <h1>No pudimos mostrar el libro</h1>
      <button type="button" onClick={reset}>Reintentar</button>
    </main>
  );
}
