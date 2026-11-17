// Taller nivel-05/01, punto 4: la cascada y sus dos soluciones. (La tercera, Suspense por sección, se mide en e2e/n5-01.spec.ts
// y en e2e/n5-04.spec.ts con la página real: cada sección llega cuando está lista.)
import { describe, expect, it } from 'vitest';
import { enCascada, enParalelo } from '@/react-next/n5-01-servidor-cliente/cascadas';

async function tiempo<T>(f: () => Promise<T>) { const t = performance.now(); const r = await f(); return { r, ms: performance.now() - t }; }

describe('cascada de awaits', () => {
  it('en cascada suma los tiempos; con Promise.all se queda en el mayor', async () => {
    const a = await tiempo(() => enCascada('c1'));
    const b = await tiempo(() => enParalelo('c1'));
    expect(a.ms).toBeGreaterThanOrEqual(195);       // 100 ms + 100 ms
    expect(b.ms).toBeLessThan(170);                  // ~100 ms
    expect(b.r).toEqual(a.r);                        // mismo resultado
  });
});
