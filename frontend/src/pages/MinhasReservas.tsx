import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../services/api';
import { useSessao } from '../hooks/useSessao';
import type { Reserva } from '../types';
import { ReservaCard } from '../components/ReservaCard';

export function MinhasReservas() {
  const { usuario, perfil } = useSessao();
  const navigate = useNavigate();
  const [reservas, setReservas] = useState<Reserva[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  async function carregar() {
    setCarregando(true);
    try {
      setReservas(await api.reservas(usuario));
      setErro(null);
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setCarregando(false);
    }
  }

  useEffect(() => {
    void carregar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [usuario.id]);

  async function cancelar(id: number) {
    if (!window.confirm('Confirma o cancelamento desta reserva?')) return;
    try {
      await api.cancelar(usuario, id);
      void carregar();
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  const podeAlterar = (r: Reserva) =>
    r.status === 'PREVISTA' || r.status === 'EM_ANDAMENTO';

  return (
    <section>
      <div className="pagina__head">
        <h1>{perfil === 'GESTOR' ? 'Todas as reservas' : 'Minhas reservas'}</h1>
        <Link className="botao botao--primario" to="/nova-reserva">
          Nova reserva
        </Link>
      </div>

      {carregando && <p role="status">Carregando…</p>}
      {erro && (
        <p className="erro" role="alert">
          {erro}
        </p>
      )}

      {!carregando && reservas.length === 0 && (
        <p>Nenhuma reserva encontrada.</p>
      )}

      <div className="grade">
        {reservas.map((r) => (
          <ReservaCard
            key={r.RESE_ID}
            reserva={r}
            acoes={
              podeAlterar(r) ? (
                <>
                  <button
                    className="botao"
                    onClick={() => navigate(`/nova-reserva?id=${r.RESE_ID}`)}
                  >
                    Editar
                  </button>
                  <button
                    className="botao botao--perigo"
                    onClick={() => cancelar(r.RESE_ID)}
                  >
                    Cancelar
                  </button>
                </>
              ) : null
            }
          />
        ))}
      </div>
    </section>
  );
}
