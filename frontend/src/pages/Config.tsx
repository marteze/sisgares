import { useEffect, useState } from 'react';
import { api } from '../services/api';
import { useSessao } from '../hooks/useSessao';
import type { Configuracao } from '../types';

export function Config() {
  const { usuario } = useSessao();
  const [cfg, setCfg] = useState<Configuracao | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [ok, setOk] = useState(false);

  useEffect(() => {
    api
      .config(usuario)
      .then(setCfg)
      .catch((e) => setErro((e as Error).message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [usuario.id]);

  async function salvar(e: React.FormEvent) {
    e.preventDefault();
    if (!cfg) return;
    setErro(null);
    setOk(false);
    try {
      const atualizada = await api.salvarConfig(usuario, cfg);
      setCfg(atualizada);
      setOk(true);
    } catch (err) {
      setErro((err as Error).message);
    }
  }

  if (!cfg) {
    return (
      <section>
        <h1>Configurações</h1>
        {erro ? (
          <p className="erro" role="alert">
            {erro}
          </p>
        ) : (
          <p role="status">Carregando…</p>
        )}
      </section>
    );
  }

  return (
    <section>
      <h1>Configurações do sistema</h1>
      <p className="pagina__sub">
        Parâmetros globais usados nas validações de reserva (RF09).
      </p>

      {erro && (
        <p className="erro" role="alert">
          {erro}
        </p>
      )}
      {ok && (
        <p className="sucesso" role="status">
          Configurações salvas.
        </p>
      )}

      <form className="form" onSubmit={salvar}>
        <div className="form__grupo">
          <label htmlFor="antec">Antecedência mínima (minutos)</label>
          <input
            id="antec"
            type="number"
            min={0}
            value={cfg.antecedenciaMinimaMin}
            onChange={(e) =>
              setCfg({ ...cfg, antecedenciaMinimaMin: Number(e.target.value) })
            }
          />
        </div>

        <div className="form__linha">
          <div className="form__grupo">
            <label htmlFor="hmin">Horário mínimo (global)</label>
            <input
              id="hmin"
              type="time"
              value={cfg.horarioMinGlobal}
              onChange={(e) =>
                setCfg({ ...cfg, horarioMinGlobal: e.target.value })
              }
            />
          </div>
          <div className="form__grupo">
            <label htmlFor="hmax">Horário máximo (global)</label>
            <input
              id="hmax"
              type="time"
              value={cfg.horarioMaxGlobal}
              onChange={(e) =>
                setCfg({ ...cfg, horarioMaxGlobal: e.target.value })
              }
            />
          </div>
        </div>

        <div className="form__grupo">
          <label htmlFor="margem">Margem de tolerância (minutos)</label>
          <input
            id="margem"
            type="number"
            min={0}
            value={cfg.margemToleranciaMin}
            onChange={(e) =>
              setCfg({ ...cfg, margemToleranciaMin: Number(e.target.value) })
            }
          />
        </div>

        <div className="form__grupo">
          <label htmlFor="snp">Endpoint da API do SNP</label>
          <input
            id="snp"
            value={cfg.snpEndpoint}
            onChange={(e) => setCfg({ ...cfg, snpEndpoint: e.target.value })}
          />
        </div>

        <div className="form__acoes">
          <button type="submit" className="botao botao--primario">
            Salvar configurações
          </button>
        </div>
      </form>
    </section>
  );
}
