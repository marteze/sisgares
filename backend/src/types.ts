// Tipos do domínio do módulo de Reservas.
// Nomes de campos seguem o padrão do sistema Solare (prefixo por entidade).
// Cobre os requisitos RF01-RF17.

export interface UnidadeMacro {
  UNID_ID: number;
  UNID_DESC: string;
}

export interface Ambiente {
  AMBI_ID: number;
  AMBI_DESC: string;
  AMBI_ST_ATIVO: 'S' | 'N';
  AMBI_ID_PAI: number | null;
  AMBI_UNID_ID: number | null; // RF02: unidade macro
  AMBI_COMPLEMENTO?: string | null;
}

export interface Disposicao {
  DISP_ID: number;
  DISP_DESC: string;
  DISP_ST_ATIVO: 'S' | 'N';
  DISP_ICONE_ARQUIVO: string;
}

export interface GrupoRecurso {
  GREC_ID: number;
  GREC_DESC: string;
  GREC_ORDEM: number;
  GREC_ST_ATIVO: 'S' | 'N';
}

export interface Recurso {
  RECU_ID: number;
  RECU_DESC: string;
  RECU_GREC_ID: number;
  RECU_ST_LIMITADO: 'S' | 'N';
  RECU_DISPONIBILIDADE: number;
  RECU_ST_ATIVO: 'S' | 'N';
  RECU_ICONE_ARQUIVO: string;
  RECU_UNID_ID: number | null; // RF06: null = disponível em qualquer unidade
}

export interface Envolvido {
  ENVO_ID: number;
  ENVO_DESC: string;
  ENVO_EMAIL: string;
  ENVO_ST_ATIVO: 'S' | 'N';
  ENVO_EMAILS_EXTRA?: string | null; // RF01: lista de e-mails arbitrária (separada por ;)
}

// RF03: vínculo ambiente-setor, com código de serviço SNP opcional.
export interface EnvolvidoAmbiente {
  EAMB_ID: number;
  EAMB_ENVO_ID: number;
  EAMB_AMBI_ID: number;
  EAMB_SNP_SERVICO?: string | null;
}

// RF07: vínculo recurso-setor, com código de serviço SNP opcional.
export interface EnvolvidoRecurso {
  EREC_ID: number;
  EREC_ENVO_ID: number;
  EREC_RECU_ID: number;
  EREC_SNP_SERVICO?: string | null;
}

// RF08: vínculo recurso-ambiente (recurso restrito a ambientes).
export interface VinculoRecurso {
  VREC_ID: number;
  VREC_RECU_ID: number;
  VREC_AMBI_ID: number;
}

export interface PeriodoReserva {
  PRES_ID: number;
  PRES_RESE_ID: number;
  PRES_DTHR_INICIO: string; // ISO 8601 após normalização
  PRES_DTHR_TERMINO: string;
}

export interface Solicitacao {
  SOLI_ID: number;
  SOLI_RESE_ID: number;
  SOLI_RECU_ID: number;
  SOLI_QTD: number | null;
}

// RF10: status derivado do horário + cancelamento.
export type StatusReserva =
  | 'PREVISTA'
  | 'EM_ANDAMENTO'
  | 'TRANSCORRIDA'
  | 'CANCELADA';

// RF14: pedido registrado no sistema nacional de pedidos (SNP), simulado.
export interface PedidoSNP {
  SNP_ID: number;
  SNP_RESE_ID: number;
  SNP_CODIGO: string; // número do pedido (ex: "SNP-2026-000123")
  SNP_SERVICO: string; // código de serviço do catálogo
  SNP_SETOR_ID: number;
  SNP_LINK: string; // link para o pedido no sistema institucional
}

// Entidade "reserva" (RF10). Não há CSV próprio; é inferida/criada pela aplicação.
export interface Reserva {
  RESE_ID: number;
  RESE_FINALIDADE: string; // RF10 (obrigatório)
  RESE_PARTICIPANTES: number; // RF10 (obrigatório)
  RESE_AMBI_ID: number | null; // null = "Não solicitado / local próprio"
  RESE_COMPLEMENTO: string | null; // RF10: obrigatório quando sem ambiente
  RESE_DISP_ID: number | null;
  RESE_UNID_ID: number | null;
  RESE_SOLICITANTE: string;
  RESE_SOLICITANTE_NOME: string;
  RESE_CANCELADA: boolean; // RF10/RF15
  RESE_DTHR_ALTERACAO: string; // RF10: última alteração
  periodos: PeriodoReserva[];
  solicitacoes: Solicitacao[];
  snps: PedidoSNP[]; // RF14
}

// RF09: configurações do administrador (global e por unidade).
export interface Configuracao {
  antecedenciaMinimaMin: number; // minutos
  horarioMinGlobal: string; // "HH:MM"
  horarioMaxGlobal: string; // "HH:MM"
  margemToleranciaMin: number; // RF11: 30 min
  snpEndpoint: string; // RF09/RF14
  porUnidade: Record<
    number,
    { horarioMin?: string; horarioMax?: string } | undefined
  >;
}

// RF01/RF16/RF17: perfis. ADMIN para configurações.
export type Perfil = 'SOLICITANTE' | 'GESTOR' | 'ATENDENTE' | 'ADMIN';

export interface Usuario {
  id: string;
  nome: string;
  email: string;
  perfil: Perfil;
  setorId?: number; // para ATENDENTE
  unidadeId?: number;
}
