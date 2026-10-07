// Tipos espelhando as respostas da API (RF01-RF17).

export type Perfil = 'SOLICITANTE' | 'GESTOR' | 'ATENDENTE' | 'ADMIN';

export type StatusReserva =
  | 'PREVISTA'
  | 'EM_ANDAMENTO'
  | 'TRANSCORRIDA'
  | 'CANCELADA';

export interface UnidadeMacro {
  UNID_ID: number;
  UNID_DESC: string;
}

export interface Ambiente {
  AMBI_ID: number;
  AMBI_DESC: string;
  AMBI_ST_ATIVO: 'S' | 'N';
  AMBI_ID_PAI: number | null;
  AMBI_UNID_ID: number | null;
}

export interface GrupoRecurso {
  GREC_ID: number;
  GREC_DESC: string;
  GREC_ORDEM: number;
}

export interface Recurso {
  RECU_ID: number;
  RECU_DESC: string;
  RECU_GREC_ID: number;
  RECU_ST_LIMITADO: 'S' | 'N';
  RECU_DISPONIBILIDADE: number;
  RECU_ICONE_ARQUIVO: string;
  RECU_UNID_ID: number | null;
}

export interface Disposicao {
  DISP_ID: number;
  DISP_DESC: string;
  DISP_ICONE_ARQUIVO: string;
}

export interface Envolvido {
  ENVO_ID: number;
  ENVO_DESC: string;
  ENVO_EMAIL: string;
}

export interface PeriodoReserva {
  PRES_ID: number;
  PRES_RESE_ID: number;
  PRES_DTHR_INICIO: string;
  PRES_DTHR_TERMINO: string;
}

export interface RecursoReserva {
  recursoId: number;
  descricao: string;
  icone: string;
  quantidade: number | null;
}

export interface SetorEnvolvido {
  id: number;
  nome: string;
  email: string;
}

export interface PedidoSNP {
  SNP_ID: number;
  SNP_RESE_ID: number;
  SNP_CODIGO: string;
  SNP_SERVICO: string;
  SNP_SETOR_ID: number;
  SNP_LINK: string;
}

export interface Reserva {
  RESE_ID: number;
  RESE_FINALIDADE: string;
  RESE_PARTICIPANTES: number;
  RESE_AMBI_ID: number | null;
  RESE_COMPLEMENTO: string | null;
  RESE_DISP_ID: number | null;
  RESE_UNID_ID: number | null;
  RESE_SOLICITANTE: string;
  RESE_SOLICITANTE_NOME: string;
  RESE_CANCELADA: boolean;
  RESE_DTHR_ALTERACAO: string;
  periodos: PeriodoReserva[];
  status: StatusReserva;
  ambienteDescricao: string | null;
  disposicaoDescricao: string | null;
  disposicaoIcone: string | null;
  recursos: RecursoReserva[];
  setoresEnvolvidos: SetorEnvolvido[];
  snps: PedidoSNP[];
}

export interface Configuracao {
  antecedenciaMinimaMin: number;
  horarioMinGlobal: string;
  horarioMaxGlobal: string;
  margemToleranciaMin: number;
  snpEndpoint: string;
  porUnidade: Record<number, { horarioMin?: string; horarioMax?: string } | undefined>;
}

export interface NovaReservaPayload {
  finalidade: string;
  participantes: number;
  ambienteId: number | null;
  complemento: string | null;
  disposicaoId: number | null;
  unidadeId: number | null;
  periodos: { inicio: string; termino: string }[];
  recursos: { recursoId: number; quantidade: number | null }[];
}

export interface Ocupacao {
  reservaId: number;
  ambienteId: number;
  inicio: string;
  termino: string;
  finalidade: string;
  solicitante: string;
  doProprioAmbiente: boolean;
  podeEditar: boolean;
}

export interface GradeResposta {
  ambienteId: number;
  config: {
    antecedenciaMinimaMin: number;
    margemToleranciaMin: number;
    horarioMin: string;
    horarioMax: string;
  };
  ocupacoes: Ocupacao[];
}

export interface OcupacaoGeral {
  reservaId: number;
  ambienteId: number;
  inicio: string;
  termino: string;
  finalidade: string;
  solicitante: string;
  participantes: number;
  status: StatusReserva;
  podeEditar: boolean;
}

export interface VisaoGeralResposta {
  config: {
    antecedenciaMinimaMin: number;
    margemToleranciaMin: number;
    horarioMin: string;
    horarioMax: string;
  };
  ambientes: { ambienteId: number; descricao: string }[];
  ocupacoes: OcupacaoGeral[];
}

export interface CardAtendente {
  reservaId: number;
  inicio: string;
  termino: string;
  finalidade: string;
  solicitante: string;
  participantes: number;
  status: StatusReserva;
  recursos: string[];
  snps: { codigo: string; link: string }[];
}
