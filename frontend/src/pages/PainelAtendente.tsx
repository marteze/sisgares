import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../services/api';
import { useSessao } from '../hooks/useSessao';
import type { CardAtendente } from '../types';
import { StatusBadge } from '../components/StatusBadge';
import { formatarHora } from '../utils/format';

function ymd(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
    d.getDate()
  ).padStart(2, '0')}`;
}

export function PainelAtendente() {
  const { usuario } = useSessao();
  const navigate = useNavigate();
  const [cards, setCards] = useState<CardAtendente[]>([]);
  const [dataRef, setDataRef] = useState(ymd(new Date()));
  const [numColunas, setNumColunas] = useState(5);
  const [mostrarFds, setMostrarFds] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    api
      .atendente(usuario, usuario.setorId)
      .then((r) => setCards(r.cards))
      .catch((e) => setErro((e as Error).message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [usuario.id]);

  const colunas = useMemo(() => {
    const base = new Date(`${dataRef}T00:00:00`);
    const dias: Date[] = [];
    let i = 0;
    while (dias.length < numColunas && i < numColunas * 3) {
      const d = new Date(base);
      d.setDate(base.getDate() + i);
      const fds = d.getDay() === 0 || d.getDay() === 6;
      if (mostrarFds || !fds) dias.push(d);
      i++;
    }
    return dias;
  }, [dataRef, numColunas, mostrarFds]);

  function cardsDoDia(dia: Date): CardAtendente[] {
    const chave = ymd(dia);
    return cards
      .filter((c) => ymd(new Date(c.inicio)) === chave)
      .sort((a, b) => a.inicio.localeCompare(b.inicio));
  }

  return (
    <section>
      <h1>Painel do atendente</h1>
      <p className="pagina__sub">
        Reservas que demandam atuação do seu setor, organizadas por data.
      </p>

      <div className="controles">
        <div className="form__grupo">
          <label htmlFor="data-ref-at">Data de referência</label>
          <input
            id="data-ref-at"
            type="date"
            value={dataRef}
            onChange={(e) => setDataRef(e.target.value)}
          />
        </div>
        <div className="form__grupo">
          <label htmlFor="num-col-at">Colunas</label>
          <input
            id="num-col-at"
            type="number"
            min={1}
            max={14}
            value={numColunas}
            onChange={(e) => setNumColunas(Number(e.target.value))}
          />
        </div>
        <div className="form__grupo form__grupo--check">
          <label>
            <input
              type="checkbox"
              checked={mostrarFds}
              onChange={(e) => setMostrarFds(e.target.checked)}
            />{' '}
            Exibir fins de semana
          </label>
        </div>
      </div>

      {erro && (
        <p className="erro" role="alert">
          {erro}
        </p>
      )}

      <div className="colunas-atendente">
        {colunas.map((dia) => {
          const doDia = cardsDoDia(dia);
          return (
            <div className="coluna-dia" key={dia.toISOString()}>
              <h2 className="coluna-dia__titulo">
                {dia.toLocaleDateString('pt-BR', {
                  weekday: 'short',
                  day: '2-digit',
                  month: '2-digit',
                })}
              </h2>
              {doDia.length === 0 && (
                <p className="coluna-dia__vazio">Sem reservas</p>
              )}
              {doDia.map((c, i) => (
                <article className="card-atendente" key={`${c.reservaId}-${i}`}>
                  <header className="card-atendente__head">
                    <strong>
                      {formatarHora(c.inicio)}–{formatarHora(c.termino)}
                    </strong>
                    <StatusBadge status={c.status} />
                  </header>
                  <p className="card-atendente__fin">{c.finalidade}</p>
                  <p className="card-atendente__meta">
                    {c.solicitante} · {c.participantes} participantes
                  </p>
                  {c.recursos.length > 0 && (
                    <p className="card-atendente__rec">{c.recursos.join(', ')}</p>
                  )}
                  {c.snps.length > 0 && (
                    <p className="card-atendente__snp">
                      {c.snps.map((s, j) => (
                        <span key={s.codigo}>
                          {j > 0 && ', '}
                          <a href={s.link} target="_blank" rel="noreferrer">
                            {s.codigo}
                          </a>
                        </span>
                      ))}
                    </p>
                  )}
                  <button
                    className="botao"
                    onClick={() => navigate(`/nova-reserva?id=${c.reservaId}`)}
                  >
                    Abrir reserva
                  </button>
                </article>
              ))}
            </div>
          );
        })}
      </div>
    </section>
  );
}
