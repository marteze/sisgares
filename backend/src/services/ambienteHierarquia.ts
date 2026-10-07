// RF02/RF11: hierarquia de ambientes (pai/filho).
import { getDb } from './dataStore.js';

// Retorna o conjunto de IDs "relacionados" a um ambiente para fins de conflito:
// o próprio, todos os ancestrais e todos os descendentes.
export function ambientesRelacionados(ambienteId: number): Set<number> {
  const db = getDb();
  const resultado = new Set<number>([ambienteId]);

  // Ancestrais
  let atual = db.ambientes.find((a) => a.AMBI_ID === ambienteId);
  while (atual && atual.AMBI_ID_PAI !== null) {
    const pai = atual.AMBI_ID_PAI;
    if (resultado.has(pai)) break; // proteção contra ciclo
    resultado.add(pai);
    atual = db.ambientes.find((a) => a.AMBI_ID === pai);
  }

  // Descendentes (BFS)
  const fila = [ambienteId];
  while (fila.length > 0) {
    const id = fila.shift()!;
    for (const filho of db.ambientes.filter((a) => a.AMBI_ID_PAI === id)) {
      if (!resultado.has(filho.AMBI_ID)) {
        resultado.add(filho.AMBI_ID);
        fila.push(filho.AMBI_ID);
      }
    }
  }

  return resultado;
}
