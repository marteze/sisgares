import { Link } from 'react-router-dom';
import { useSessao } from '../hooks/useSessao';

export function Home() {
  const { perfil } = useSessao();

  return (
    <section className="hero">
      <h1>Reservas de Ambientes e Recursos</h1>
      <p className="hero__sub">
        Solicite e gerencie reservas de ambientes físicos, recursos tecnológicos e
        serviços para eventos das unidades do MPF, com integração ao sistema
        nacional de pedidos (SNP).
      </p>

      <div className="hero__atalhos">
        {(perfil === 'SOLICITANTE' || perfil === 'GESTOR') && (
          <>
            <Link className="botao botao--primario" to="/painel">
              Abrir painel-grade
            </Link>
            <Link className="botao" to="/nova-reserva">
              Nova reserva
            </Link>
            <Link className="botao" to="/minhas-reservas">
              {perfil === 'GESTOR' ? 'Todas as reservas' : 'Minhas reservas'}
            </Link>
          </>
        )}
        {perfil === 'ATENDENTE' && (
          <Link className="botao botao--primario" to="/atendente">
            Ver painel do atendente
          </Link>
        )}
        {perfil === 'ADMIN' && (
          <Link className="botao botao--primario" to="/config">
            Abrir configurações
          </Link>
        )}
      </div>

      <p className="hero__dica">
        Use o seletor de <strong>Perfil (demo)</strong> no topo para alternar entre
        as visões de solicitante, gestor, atendente e administrador.
      </p>
    </section>
  );
}
