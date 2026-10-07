// Sessão de usuário mock. Permite trocar de perfil para demonstrar as visões.
// Em produção seria substituído pela sessão do AWS Cognito.
import {
  createContext,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import type { SessaoUsuario } from '../services/api';
import type { Perfil } from '../types';

export const USUARIOS_DEMO: Record<Perfil, SessaoUsuario> = {
  SOLICITANTE: {
    id: 'u-sol',
    nome: 'Ana Solicitante',
    email: 'ana@mpf.mp.br',
    perfil: 'SOLICITANTE',
    unidadeId: 1,
  },
  GESTOR: {
    id: 'u-ges',
    nome: 'Carlos Gestor',
    email: 'carlos@mpf.mp.br',
    perfil: 'GESTOR',
    unidadeId: 1,
  },
  ATENDENTE: {
    id: 'u-aten',
    nome: 'Marcos (SELOG)',
    email: 'PRCE-SELOG@mpf.mp.br',
    perfil: 'ATENDENTE',
    setorId: 3,
    unidadeId: 1,
  },
  ADMIN: {
    id: 'u-adm',
    nome: 'Admin do Sistema',
    email: 'admin@mpf.mp.br',
    perfil: 'ADMIN',
    unidadeId: 1,
  },
};

interface SessaoContextValue {
  usuario: SessaoUsuario;
  perfil: Perfil;
  trocarPerfil: (p: Perfil) => void;
}

const SessaoContext = createContext<SessaoContextValue | null>(null);

export function SessaoProvider({ children }: { children: ReactNode }) {
  const [perfil, setPerfil] = useState<Perfil>('SOLICITANTE');

  const value = useMemo<SessaoContextValue>(
    () => ({
      usuario: USUARIOS_DEMO[perfil],
      perfil,
      trocarPerfil: setPerfil,
    }),
    [perfil]
  );

  return <SessaoContext.Provider value={value}>{children}</SessaoContext.Provider>;
}

export function useSessao(): SessaoContextValue {
  const ctx = useContext(SessaoContext);
  if (!ctx) throw new Error('useSessao deve ser usado dentro de SessaoProvider');
  return ctx;
}
