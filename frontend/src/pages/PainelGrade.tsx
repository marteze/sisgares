import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../services/api';
import { useSessao } from '../hooks/useSessao';
import type {
  Ambiente,
  GradeResposta,
  Ocupacao,
  VisaoGeralResposta,
} from '../types';
import { formatarHora } from '../utils/format';

const SLOT_MIN = 30;

type Modo = 'sala' | 'geral';

type EstadoCelula =
  | { tipo: 'LIVRE' }
  | { tipo: 'OCUPADO'; ocupacao: Ocupacao }
  | { tipo: 'MARGEM' }
  | { tipo: 'PASSADO' }
  | { tipo: 'SEM_ANTECEDENCIA' };

function ymd(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
    d.getDate()
  ).padStart(2, '0')}`;
}

function parseHHMM(hhmm: string): { h: number; m: number } {
  const [h, m] = hhmm.split(':').map(Number);
  return { h: h || 0, m: m || 0 };
}

export function PainelGrade() {
  const { usuario } = useSessao();
  const navigate = useNavigate();

  const [modo, setModo] = useState<Modo>('sala');
  const [ambientes, setAmbientes] = useState<Ambiente[]>([]);
  const [ambienteId, setAmbienteId] = useState<number | null>(null);
  const [grade, setGrade] = useState<GradeResposta | null>(null);
  const [geral, setGeral] = useState<VisaoGeralResposta | null>(null);
  const [dataRef, setDataRef] = useState<string>(ymd(new Date()));
  const [numColunas, setNumColunas] = useState(5);
  const [mostrarFds, setMostrarFds] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    api
      .ambientes(usuario, usuario.unidadeId)
      .then((a) => {
        setAmbientes(a);
        if (a.length && ambienteId === null) setAmbienteId(a[0].AMBI_ID);
      })
      .catch((e) => setErro((e as Error).message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [usuario.id]);

  // Carrega a grade da sala selecionada (modo "sala").
  useEffect(() => {
    if (modo !== 'sala' || !ambienteId) return;
    api
      .grade(usuario, ambienteId)
      .then(setGrade)
      .catch((e) => setErro((e as Error).message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modo, ambienteId, usuario.id]);

  // Carrega a visão geral de todas as salas (modo "geral").
  useEffect(() => {
    if (modo !== 'geral') return;
    api
      .visaoGeral(usuario)
      .then(setGeral)
      .catch((e) => setErro((e as Error).message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modo, usuario.id]);

  // Colunas de datas (pulando fins de semana se configurado).
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

  // Linhas de horário (a cada 30 min dentro da faixa configurável).
  const linhas = useMemo(() => {
    if (!grade) return [];
    const { h: hi } = parseHHMM(grade.config.horarioMin);
    const { h: hf, m: mf } = parseHHMM(grade.config.horarioMax);
    const slots: { h: number; m: number }[] = [];
    let total = hi * 60;
    const fim = hf * 60 + mf;
    while (total < fim) {
      slots.push({ h: Math.floor(total / 60), m: total % 60 });
      total += SLOT_MIN;
    }
    return slots;
  }, [grade]);

  function estadoCelula(dia: Date, slot: { h: number; m: number }): EstadoCelula {
    if (!grade) return { tipo: 'LIVRE' };
    const inicio = new Date(dia);
    inicio.setHours(slot.h, slot.m, 0, 0);
    const fim = new Date(inicio.getTime() + SLOT_MIN * 60000);
    const agora = new Date();
    const margemMs = grade.config.margemToleranciaMin * 60000;
    const antecMs = grade.config.antecedenciaMinimaMin * 60000;

    // Ocupação direta
    for (const o of grade.ocupacoes) {
      const oi = new Date(o.inicio).getTime();
      const of = new Date(o.termino).getTime();
      if (inicio.getTime() < of && oi < fim.getTime()) {
        return { tipo: 'OCUPADO', ocupacao: o };
      }
    }
    // Margem de tolerância (RF16): célula encosta num período ocupado.
    for (const o of grade.ocupacoes) {
      const oi = new Date(o.inicio).getTime();
      const of = new Date(o.termino).getTime();
      if (
        (inicio.getTime() >= of && inicio.getTime() < of + margemMs) ||
        (fim.getTime() <= oi && fim.getTime() > oi - margemMs)
      ) {
        return { tipo: 'MARGEM' };
      }
    }
    // Horário ultrapassado
    if (fim.getTime() <= agora.getTime()) return { tipo: 'PASSADO' };
    // Sem antecedência mínima
    if (inicio.getTime() - agora.getTime() < antecMs)
      return { tipo: 'SEM_ANTECEDENCIA' };
    return { tipo: 'LIVRE' };
  }

  function reservar(dia: Date, slot: { h: number; m: number }) {
    const inicio = new Date(dia);
    inicio.setHours(slot.h, slot.m, 0, 0);
    const termino = new Date(inicio.getTime() + SLOT_MIN * 60000);
    navigate(
      `/nova-reserva?ambienteId=${ambienteId}&inicio=${fmtLocal(inicio)}&termino=${fmtLocal(
        termino
      )}`
    );
  }

  // Ocupações da visão geral para um ambiente numa data (modo "geral").
  function ocupacoesGeralDia(ambId: number, dia: Date) {
    if (!geral) return [];
    const chave = ymd(dia);
    return geral.ocupacoes
      .filter((o) => o.ambienteId === ambId && ymd(new Date(o.inicio)) === chave)
      .sort((a, b) => a.inicio.localeCompare(b.inicio));
  }

  return (
    <section>
      <h1>Painel-grade de reservas</h1>

      <div className="segmento" role="tablist" aria-label="Modo de visualização">
        <button
          role="tab"
          aria-selected={modo === 'sala'}
          className={`segmento__btn ${modo === 'sala' ? 'segmento__btn--ativo' : ''}`}
          onClick={() => setModo('sala')}
        >
          Por sala
        </button>
        <button
          role="tab"
          aria-selected={modo === 'geral'}
          className={`segmento__btn ${modo === 'geral' ? 'segmento__btn--ativo' : ''}`}
          onClick={() => setModo('geral')}
        >
          Visão geral (todas as salas)
        </button>
      </div>

      <div className="controles">
        {modo === 'sala' && (
          <div className="form__grupo">
            <label htmlFor="amb-grade">Ambiente</label>
            <select
              id="amb-grade"
              value={ambienteId ?? ''}
              onChange={(e) => setAmbienteId(Number(e.target.value))}
            >
              {ambientes.map((a) => (
                <option key={a.AMBI_ID} value={a.AMBI_ID}>
                  {a.AMBI_DESC}
                </option>
              ))}
            </select>
          </div>
        )}
        <div className="form__grupo">
          <label htmlFor="data-ref">Data de referência</label>
          <input
            id="data-ref"
            type="date"
            value={dataRef}
            onChange={(e) => setDataRef(e.target.value)}
          />
        </div>
        <div className="form__grupo">
          <label htmlFor="num-col">Colunas</label>
          <input
            id="num-col"
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

      {modo === 'sala' ? (
        <div className="grade-scroll">
          <table className="grade-tabela">
            <caption className="sr-only">
              Grade de disponibilidade por data e horário
            </caption>
            <thead>
              <tr>
                <th scope="col">Horário</th>
                {colunas.map((d) => (
                  <th scope="col" key={d.toISOString()}>
                    {d.toLocaleDateString('pt-BR', {
                      weekday: 'short',
                      day: '2-digit',
                      month: '2-digit',
                    })}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {linhas.map((slot) => (
                <tr key={`${slot.h}:${slot.m}`}>
                  <th scope="row" className="grade-hora">
                    {String(slot.h).padStart(2, '0')}:
                    {String(slot.m).padStart(2, '0')}
                  </th>
                  {colunas.map((dia) => {
                    const est = estadoCelula(dia, slot);
                    const hhmm = `${String(slot.h).padStart(2, '0')}:${String(
                      slot.m
                    ).padStart(2, '0')}`;
                    if (est.tipo === 'LIVRE') {
                      return (
                        <td
                          key={dia.toISOString()}
                          className="celula celula--livre"
                        >
                          <button
                            className="celula-link"
                            onClick={() => reservar(dia, slot)}
                          >
                            Reservar às {hhmm}
                          </button>
                        </td>
                      );
                    }
                    if (est.tipo === 'OCUPADO') {
                      const o = est.ocupacao;
                      return (
                        <td
                          key={dia.toISOString()}
                          className={`celula celula--ocupado ${
                            o.doProprioAmbiente ? '' : 'celula--relacionado'
                          }`}
                          title={`${o.finalidade} — ${o.solicitante}`}
                        >
                          {o.podeEditar ? (
                            <button
                              className="celula-link"
                              onClick={() =>
                                navigate(`/nova-reserva?id=${o.reservaId}`)
                              }
                            >
                              {o.finalidade}
                            </button>
                          ) : (
                            <span>Reservado</span>
                          )}
                        </td>
                      );
                    }
                    const rotulo =
                      est.tipo === 'MARGEM'
                        ? 'Margem de tolerância'
                        : est.tipo === 'PASSADO'
                        ? 'Horário ultrapassado'
                        : 'Sem antecedência mínima';
                    return (
                      <td
                        key={dia.toISOString()}
                        className={`celula celula--${est.tipo.toLowerCase()}`}
                      >
                        <span>{rotulo}</span>
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="grade-scroll">
          <table className="grade-tabela grade-geral">
            <caption className="sr-only">
              Reservas de todas as salas por data
            </caption>
            <thead>
              <tr>
                <th scope="col">Sala</th>
                {colunas.map((d) => (
                  <th scope="col" key={d.toISOString()}>
                    {d.toLocaleDateString('pt-BR', {
                      weekday: 'short',
                      day: '2-digit',
                      month: '2-digit',
                    })}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {(geral?.ambientes ?? []).map((amb) => (
                <tr key={amb.ambienteId}>
                  <th scope="row" className="grade-geral__sala">
                    {amb.descricao}
                  </th>
                  {colunas.map((dia) => {
                    const items = ocupacoesGeralDia(amb.ambienteId, dia);
                    return (
                      <td key={dia.toISOString()} className="celula-geral">
                        {items.length === 0 ? (
                          <span className="celula-geral__vazio">—</span>
                        ) : (
                          <ul className="lista-limpa">
                            {items.map((o, i) => (
                              <li key={`${o.reservaId}-${i}`}>
                                <button
                                  className="reserva-chip"
                                  title={`${o.finalidade} — ${o.solicitante} (${o.participantes} part.)`}
                                  onClick={() =>
                                    o.podeEditar
                                      ? navigate(`/nova-reserva?id=${o.reservaId}`)
                                      : undefined
                                  }
                                  disabled={!o.podeEditar}
                                >
                                  <strong>
                                    {formatarHora(o.inicio)}–
                                    {formatarHora(o.termino)}
                                  </strong>{' '}
                                  {o.finalidade}
                                </button>
                              </li>
                            ))}
                          </ul>
                        )}
                      </td>
                    );
                  })}
                </tr>
              ))}
              {geral && geral.ambientes.length === 0 && (
                <tr>
                  <td colSpan={colunas.length + 1}>Nenhum ambiente ativo.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function fmtLocal(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(
    d.getDate()
  ).padStart(2, '0')}T${String(d.getHours()).padStart(2, '0')}:${String(
    d.getMinutes()
  ).padStart(2, '0')}`;
}
