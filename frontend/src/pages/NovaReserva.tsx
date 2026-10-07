import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../services/api';
import { useSessao } from '../hooks/useSessao';
import type {
  Ambiente,
  Disposicao,
  GrupoRecurso,
  NovaReservaPayload,
  Recurso,
} from '../types';
import { iconeDisposicao, iconeRecurso } from '../utils/format';
import { ImagemAmpliavel } from '../components/ImagemAmpliavel';

interface PeriodoForm {
  inicio: string;
  termino: string;
  conflito?: boolean;
  conflitoMsg?: string;
}

interface Selecionado {
  recursoId: number;
  quantidade: number | null;
}

export function NovaReserva() {
  const { usuario } = useSessao();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const edicaoId = params.get('id') ? Number(params.get('id')) : null;

  const [ambientes, setAmbientes] = useState<Ambiente[]>([]);
  const [recursos, setRecursos] = useState<Recurso[]>([]);
  const [grupos, setGrupos] = useState<GrupoRecurso[]>([]);
  const [disposicoes, setDisposicoes] = useState<Disposicao[]>([]);

  const [finalidade, setFinalidade] = useState('');
  const [participantes, setParticipantes] = useState<number>(1);
  const [ambienteId, setAmbienteId] = useState<number | null>(null);
  const [complemento, setComplemento] = useState('');
  const [disposicaoId, setDisposicaoId] = useState<number | null>(null);
  const [periodos, setPeriodos] = useState<PeriodoForm[]>([
    { inicio: params.get('inicio') ?? '', termino: params.get('termino') ?? '' },
  ]);
  const [selecionados, setSelecionados] = useState<Selecionado[]>([]);

  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  // Prefill de ambiente vindo da grade (RF16).
  useEffect(() => {
    const amb = params.get('ambienteId');
    if (amb) setAmbienteId(Number(amb));
  }, [params]);

  useEffect(() => {
    Promise.all([
      api.ambientes(usuario, usuario.unidadeId),
      api.grupos(usuario),
      api.disposicoes(usuario),
    ])
      .then(([a, g, d]) => {
        setAmbientes(a);
        setGrupos(g);
        setDisposicoes(d);
      })
      .catch((e) => setErro((e as Error).message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [usuario.id]);

  // Recursos dependem do ambiente (RF08) e da unidade (RF06).
  useEffect(() => {
    api
      .recursos(usuario, {
        unidadeId: usuario.unidadeId,
        ambienteId: ambienteId ?? undefined,
      })
      .then(setRecursos)
      .catch((e) => setErro((e as Error).message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [usuario.id, ambienteId]);

  // Carrega reserva para edição (RF15).
  useEffect(() => {
    if (!edicaoId) return;
    api
      .reserva(usuario, edicaoId)
      .then((r) => {
        setFinalidade(r.RESE_FINALIDADE);
        setParticipantes(r.RESE_PARTICIPANTES);
        setAmbienteId(r.RESE_AMBI_ID);
        setComplemento(r.RESE_COMPLEMENTO ?? '');
        setDisposicaoId(r.RESE_DISP_ID);
        setPeriodos(
          r.periodos.map((p) => ({
            inicio: p.PRES_DTHR_INICIO.slice(0, 16),
            termino: p.PRES_DTHR_TERMINO.slice(0, 16),
          }))
        );
        setSelecionados(
          r.recursos.map((x) => ({
            recursoId: x.recursoId,
            quantidade: x.quantidade,
          }))
        );
      })
      .catch((e) => setErro((e as Error).message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [edicaoId, usuario.id]);

  const recursosPorGrupo = useMemo(
    () =>
      grupos.map((g) => ({
        grupo: g,
        itens: recursos.filter((r) => r.RECU_GREC_ID === g.GREC_ID),
      })),
    [grupos, recursos]
  );

  const dispSelecionada = disposicoes.find((d) => d.DISP_ID === disposicaoId);

  function alternarRecurso(id: number) {
    setSelecionados((prev) =>
      prev.some((s) => s.recursoId === id)
        ? prev.filter((s) => s.recursoId !== id)
        : [...prev, { recursoId: id, quantidade: 1 }]
    );
  }

  function mudarQuantidade(id: number, q: number) {
    setSelecionados((prev) =>
      prev.map((s) => (s.recursoId === id ? { ...s, quantidade: q } : s))
    );
  }

  function mudarPeriodo(idx: number, campo: 'inicio' | 'termino', valor: string) {
    setPeriodos((prev) =>
      prev.map((p, i) => (i === idx ? { ...p, [campo]: valor, conflito: undefined } : p))
    );
  }

  function adicionarPeriodo() {
    setPeriodos((prev) => [...prev, { inicio: '', termino: '' }]);
  }

  function removerPeriodo(idx: number) {
    setPeriodos((prev) => prev.filter((_, i) => i !== idx));
  }

  // RF11: verificação antecipada ao sair do campo de término.
  async function checarConflito(idx: number) {
    const p = periodos[idx];
    if (!ambienteId || !p.inicio || !p.termino) return;
    try {
      const r = await api.verificarConflito(
        usuario,
        ambienteId,
        p.inicio,
        p.termino,
        edicaoId ?? undefined
      );
      setPeriodos((prev) =>
        prev.map((x, i) =>
          i === idx
            ? {
                ...x,
                conflito: r.conflito,
                conflitoMsg: r.conflito
                  ? `Conflito com a reserva #${r.conflitos[0]?.reservaId}`
                  : undefined,
              }
            : x
        )
      );
    } catch {
      /* ignora erro de verificação antecipada */
    }
  }

  const semAmbiente = ambienteId === null;

  async function enviar(e: React.FormEvent) {
    e.preventDefault();
    setErro(null);

    if (!finalidade.trim()) return setErro('Informe a finalidade da reserva.');
    if (!participantes || participantes < 1)
      return setErro('Informe a quantidade de participantes.');
    if (semAmbiente && !complemento.trim())
      return setErro('Sem ambiente físico, o complemento é obrigatório.');
    if (periodos.some((p) => !p.inicio || !p.termino || p.inicio >= p.termino))
      return setErro('Verifique os períodos (início antes do término).');

    const payload: NovaReservaPayload = {
      finalidade: finalidade.trim(),
      participantes,
      ambienteId,
      complemento: semAmbiente ? complemento.trim() : null,
      disposicaoId: semAmbiente ? null : disposicaoId,
      unidadeId: usuario.unidadeId ?? null,
      periodos: periodos.map((p) => ({ inicio: p.inicio, termino: p.termino })),
      recursos: selecionados,
    };

    setEnviando(true);
    try {
      if (edicaoId) await api.alterarReserva(usuario, edicaoId, payload);
      else await api.criarReserva(usuario, payload);
      navigate('/minhas-reservas');
    } catch (err) {
      setErro((err as Error).message);
      setEnviando(false);
    }
  }

  return (
    <section>
      <h1>{edicaoId ? `Editar reserva #${edicaoId}` : 'Nova reserva'}</h1>
      <p className="pagina__sub">
        O ambiente físico é opcional. Deixe em "Não solicitado / local próprio" para
        reservar apenas recursos ou serviços.
      </p>

      {erro && (
        <p className="erro" role="alert">
          {erro}
        </p>
      )}

      <form className="form" onSubmit={enviar} noValidate>
        <div className="form__grupo">
          <label htmlFor="finalidade">
            Finalidade <span aria-hidden="true">*</span>
          </label>
          <textarea
            id="finalidade"
            value={finalidade}
            onChange={(e) => setFinalidade(e.target.value)}
            rows={3}
            required
            aria-required="true"
            placeholder="Descreva a finalidade do evento"
          />
        </div>

        <div className="form__linha">
          <div className="form__grupo">
            <label htmlFor="participantes">
              Participantes estimados <span aria-hidden="true">*</span>
            </label>
            <input
              id="participantes"
              type="number"
              min={1}
              value={participantes}
              onChange={(e) => setParticipantes(Number(e.target.value))}
              required
            />
          </div>

          <div className="form__grupo">
            <label htmlFor="ambiente">Ambiente</label>
            <select
              id="ambiente"
              value={ambienteId ?? ''}
              onChange={(e) =>
                setAmbienteId(e.target.value ? Number(e.target.value) : null)
              }
            >
              <option value="">Não solicitado / local próprio</option>
              {ambientes.map((a) => (
                <option key={a.AMBI_ID} value={a.AMBI_ID}>
                  {a.AMBI_DESC}
                </option>
              ))}
            </select>
          </div>
        </div>

        {semAmbiente && (
          <div className="form__grupo">
            <label htmlFor="complemento">
              Complemento do ambiente <span aria-hidden="true">*</span>
            </label>
            <input
              id="complemento"
              value={complemento}
              onChange={(e) => setComplemento(e.target.value)}
              required
              placeholder="Ex.: Sala própria do setor, 3º andar"
            />
          </div>
        )}

        {!semAmbiente && disposicoes.length > 0 && (
          <div className="form__grupo">
            <label htmlFor="disposicao">Disposição do ambiente (opcional)</label>
            <select
              id="disposicao"
              value={disposicaoId ?? ''}
              onChange={(e) =>
                setDisposicaoId(e.target.value ? Number(e.target.value) : null)
              }
            >
              <option value="">Padrão</option>
              {disposicoes.map((d) => (
                <option key={d.DISP_ID} value={d.DISP_ID}>
                  {d.DISP_DESC}
                </option>
              ))}
            </select>
            {dispSelecionada && (
              <ImagemAmpliavel
                className="disp-preview"
                src={iconeDisposicao(dispSelecionada.DISP_ICONE_ARQUIVO)}
                alt={`Ilustração da disposição ${dispSelecionada.DISP_DESC}`}
                width={160}
              />
            )}
          </div>
        )}

        <fieldset className="form__fieldset">
          <legend>Períodos</legend>
          {periodos.map((p, idx) => (
            <div key={idx} className="periodo-linha">
              <div className="form__grupo">
                <label htmlFor={`ini-${idx}`}>Início</label>
                <input
                  id={`ini-${idx}`}
                  type="datetime-local"
                  value={p.inicio}
                  onChange={(e) => mudarPeriodo(idx, 'inicio', e.target.value)}
                  onBlur={() => checarConflito(idx)}
                  required
                />
              </div>
              <div className="form__grupo">
                <label htmlFor={`fim-${idx}`}>Término</label>
                <input
                  id={`fim-${idx}`}
                  type="datetime-local"
                  value={p.termino}
                  onChange={(e) => mudarPeriodo(idx, 'termino', e.target.value)}
                  onBlur={() => checarConflito(idx)}
                  required
                />
              </div>
              {periodos.length > 1 && (
                <button
                  type="button"
                  className="botao botao--perigo"
                  onClick={() => removerPeriodo(idx)}
                  aria-label={`Remover período ${idx + 1}`}
                >
                  Remover
                </button>
              )}
              {p.conflito && (
                <p className="erro periodo-conflito" role="alert">
                  {p.conflitoMsg}
                </p>
              )}
            </div>
          ))}
          <button type="button" className="botao" onClick={adicionarPeriodo}>
            + Adicionar período
          </button>
        </fieldset>

        <fieldset className="form__fieldset">
          <legend>Recursos e serviços</legend>
          {recursosPorGrupo.map(({ grupo, itens }) =>
            itens.length === 0 ? null : (
              <div key={grupo.GREC_ID} className="grupo-recurso">
                <h3 className="grupo-recurso__titulo">{grupo.GREC_DESC}</h3>
                <ul className="opcoes">
                  {itens.map((r) => {
                    const sel = selecionados.find((s) => s.recursoId === r.RECU_ID);
                    return (
                      <li key={r.RECU_ID} className="opcao">
                        <label className="opcao__check">
                          <input
                            type="checkbox"
                            checked={!!sel}
                            onChange={() => alternarRecurso(r.RECU_ID)}
                          />
                          <img
                            src={iconeRecurso(r.RECU_ICONE_ARQUIVO)}
                            alt=""
                            width={24}
                            height={24}
                          />
                          <span>{r.RECU_DESC}</span>
                        </label>
                        {sel && r.RECU_ST_LIMITADO === 'S' && (
                          <label className="opcao__qtd">
                            <span className="sr-only">
                              Quantidade de {r.RECU_DESC}
                            </span>
                            Qtd:
                            <input
                              type="number"
                              min={1}
                              max={r.RECU_DISPONIBILIDADE || undefined}
                              value={sel.quantidade ?? 1}
                              onChange={(e) =>
                                mudarQuantidade(r.RECU_ID, Number(e.target.value))
                              }
                            />
                          </label>
                        )}
                      </li>
                    );
                  })}
                </ul>
              </div>
            )
          )}
        </fieldset>

        <div className="form__acoes">
          <button type="submit" className="botao botao--primario" disabled={enviando}>
            {enviando
              ? 'Enviando…'
              : edicaoId
              ? 'Salvar alterações'
              : 'Solicitar reserva'}
          </button>
        </div>
      </form>
    </section>
  );
}
