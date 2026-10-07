import { NavLink } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useSessao, USUARIOS_DEMO } from '../hooks/useSessao';
import type { Perfil } from '../types';

const PERFIS: { valor: Perfil; rotulo: string }[] = [
  { valor: 'SOLICITANTE', rotulo: 'Solicitante' },
  { valor: 'GESTOR', rotulo: 'Gestor' },
  { valor: 'ATENDENTE', rotulo: 'Atendente' },
  { valor: 'ADMIN', rotulo: 'Administrador' },
];

export function Layout({ children }: { children: ReactNode }) {
  const { perfil, trocarPerfil } = useSessao();

  return (
    <div className="app">
      <a className="skip-link" href="#conteudo">
        Pular para o conteúdo
      </a>

      <header className="topo" role="banner">
        <div className="topo__marca">
          <span className="topo__logo" aria-hidden="true">
            MPF
          </span>
          <div>
            <strong>Reservas</strong>
            <small>Ambientes, recursos e serviços</small>
          </div>
        </div>

        <nav className="topo__nav" aria-label="Navegação principal">
          <NavLink to="/">Início</NavLink>
          {(perfil === 'SOLICITANTE' || perfil === 'GESTOR') && (
            <>
              <NavLink to="/painel">Painel-grade</NavLink>
              <NavLink to="/nova-reserva">Nova reserva</NavLink>
              <NavLink to="/minhas-reservas">
                {perfil === 'GESTOR' ? 'Todas as reservas' : 'Minhas reservas'}
              </NavLink>
            </>
          )}
          {perfil === 'ATENDENTE' && <NavLink to="/atendente">Painel do atendente</NavLink>}
          {perfil === 'ADMIN' && <NavLink to="/config">Configurações</NavLink>}
        </nav>

        <div className="topo__perfil">
          <label htmlFor="perfil-select">Perfil (demo):</label>
          <select
            id="perfil-select"
            value={perfil}
            onChange={(e) => trocarPerfil(e.target.value as Perfil)}
          >
            {PERFIS.map((p) => (
              <option key={p.valor} value={p.valor}>
                {p.rotulo}
              </option>
            ))}
          </select>
          <span className="topo__usuario">{USUARIOS_DEMO[perfil].nome}</span>
        </div>
      </header>

      <main id="conteudo" className="conteudo" tabIndex={-1}>
        {children}
      </main>

      <footer className="rodape" role="contentinfo">
        <small>
          Hackathon MPF &amp; AWS 2026 — Módulo de Reservas (demonstração). Dados
          fictícios; notificações por e-mail e pedidos no SNP são simulados.
        </small>
        <small className="rodape__creditos">
          Desenvolvido por <strong>Equipe Niko</strong>.
        </small>
      </footer>
    </div>
  );
}
