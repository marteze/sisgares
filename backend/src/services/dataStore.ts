// Carrega os CSVs de backend/data para memória e mantém um "banco" em memória.
// Durante o hackathon isto substitui um banco real; a interface é simples de trocar.

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import {
  parseCsvToObjects,
  parseIdMilhar,
  parseDataHora,
  toNumberOrNull,
} from '../utils/csv.js';
import type {
  Ambiente,
  Configuracao,
  Disposicao,
  Envolvido,
  EnvolvidoAmbiente,
  EnvolvidoRecurso,
  GrupoRecurso,
  PedidoSNP,
  PeriodoReserva,
  Recurso,
  Reserva,
  Solicitacao,
  UnidadeMacro,
  VinculoRecurso,
} from '../types.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const DATA_DIR = join(__dirname, '..', '..', 'data');

// Unidade macro padrão (os CSVs são da PR/CE). RF02/RF06 preveem unidades.
const UNIDADE_PADRAO = 1;

function load(file: string): Record<string, string>[] {
  const content = readFileSync(join(DATA_DIR, file), 'utf-8');
  return parseCsvToObjects(content, ',');
}

const st = (v: string) => (v === 'S' ? 'S' : 'N') as 'S' | 'N';

export interface Database {
  unidades: UnidadeMacro[];
  ambientes: Ambiente[];
  disposicoes: Disposicao[];
  grupos: GrupoRecurso[];
  recursos: Recurso[];
  envolvidos: Envolvido[];
  envolvidoAmbiente: EnvolvidoAmbiente[];
  envolvidoRecurso: EnvolvidoRecurso[];
  vinculoRecurso: VinculoRecurso[];
  reservas: Reserva[];
  snps: PedidoSNP[];
  config: Configuracao;
  seq: { reserva: number; snp: number; periodo: number; solicitacao: number };
}

let db: Database | null = null;

export function getDb(): Database {
  if (db) return db;

  const unidades: UnidadeMacro[] = [
    { UNID_ID: 1, UNID_DESC: 'PR/CE — Procuradoria da República no Ceará' },
  ];

  const ambientes: Ambiente[] = load('dados-ambiente.csv').map((r) => ({
    AMBI_ID: Number(r.AMBI_ID),
    AMBI_DESC: r.AMBI_DESC,
    AMBI_ST_ATIVO: st(r.AMBI_ST_ATIVO),
    AMBI_ID_PAI: toNumberOrNull(r.AMBI_ID_PAI),
    AMBI_UNID_ID: UNIDADE_PADRAO,
    AMBI_COMPLEMENTO: null,
  }));

  const disposicoes: Disposicao[] = load('dados-disposicao.csv').map((r) => ({
    DISP_ID: Number(r.DISP_ID),
    DISP_DESC: r.DISP_DESC,
    DISP_ST_ATIVO: st(r.DISP_ST_ATIVO),
    DISP_ICONE_ARQUIVO: r.DISP_ICONE_ARQUIVO,
  }));

  const grupos: GrupoRecurso[] = load('dados-grupo-recurso.csv').map((r) => ({
    GREC_ID: Number(r.GREC_ID),
    GREC_DESC: r.GREC_DESC,
    GREC_ORDEM: Number(r.GREC_ORDEM),
    GREC_ST_ATIVO: st(r.GREC_ST_ATIVO),
  }));

  const recursos: Recurso[] = load('dados-recurso.csv').map((r) => ({
    RECU_ID: Number(r.RECU_ID),
    RECU_DESC: r.RECU_DESC,
    RECU_GREC_ID: Number(r.RECU_GREC_ID),
    RECU_ST_LIMITADO: st(r.RECU_ST_LIMITADO),
    RECU_DISPONIBILIDADE: Number(r.RECU_DISPONIBILIDADE),
    RECU_ST_ATIVO: st(r.RECU_ST_ATIVO),
    RECU_ICONE_ARQUIVO: r.RECU_ICONE_ARQUIVO,
    RECU_UNID_ID: null, // disponível em qualquer unidade por padrão (RF06)
  }));

  const envolvidos: Envolvido[] = load('dados-envolvido.csv').map((r) => ({
    ENVO_ID: Number(r.ENVO_ID),
    ENVO_DESC: r.ENVO_DESC,
    ENVO_EMAIL: r.ENVO_EMAIL,
    ENVO_ST_ATIVO: st(r.ENVO_ST_ATIVO),
    ENVO_EMAILS_EXTRA: null,
  }));

  const envolvidoAmbiente: EnvolvidoAmbiente[] = load(
    'dados-envolvido-ambiente.csv'
  ).map((r) => ({
    EAMB_ID: Number(r.EAMB_ID),
    EAMB_ENVO_ID: Number(r.EAMB_ENVO_ID),
    EAMB_AMBI_ID: Number(r.EAMB_AMBI_ID),
    EAMB_SNP_SERVICO: null,
  }));

  const envolvidoRecurso: EnvolvidoRecurso[] = load(
    'dados-envolvido-recurso.csv'
  ).map((r) => ({
    EREC_ID: Number(r.EREC_ID),
    EREC_ENVO_ID: Number(r.EREC_ENVO_ID),
    EREC_RECU_ID: Number(r.EREC_RECU_ID),
    // RF07/RF14: alguns vínculos têm código de serviço no catálogo nacional.
    // Semeamos alguns para demonstrar o registro automático de SNP.
    EREC_SNP_SERVICO: null,
  }));

  // Semeia códigos de serviço SNP em alguns recursos para demonstrar RF14.
  const servicosSemente: Record<number, string> = {
    1: 'COPA-AGUA',
    2: 'COPA-CAFE',
    3: 'COPA-AGUA-CAFE',
  };
  for (const er of envolvidoRecurso) {
    if (servicosSemente[er.EREC_RECU_ID]) {
      er.EREC_SNP_SERVICO = servicosSemente[er.EREC_RECU_ID];
    }
  }

  const vinculoRecurso: VinculoRecurso[] = load('dados-vinculo-recurso.csv').map(
    (r) => ({
      VREC_ID: Number(r.VREC_ID),
      VREC_RECU_ID: Number(r.VREC_RECU_ID),
      VREC_AMBI_ID: Number(r.VREC_AMBI_ID),
    })
  );

  const periodos: PeriodoReserva[] = load('dados-periodo-reserva.csv').map(
    (r) => ({
      PRES_ID: parseIdMilhar(r.PRES_ID),
      PRES_RESE_ID: parseIdMilhar(r.PRES_RESE_ID),
      PRES_DTHR_INICIO: parseDataHora(r.PRES_DTHR_INICIO),
      PRES_DTHR_TERMINO: parseDataHora(r.PRES_DTHR_TERMINO),
    })
  );

  const solicitacoes: Solicitacao[] = load('dados-solicitacao.csv').map((r) => ({
    SOLI_ID: parseIdMilhar(r.SOLI_ID),
    SOLI_RESE_ID: parseIdMilhar(r.SOLI_RESE_ID),
    SOLI_RECU_ID: Number(r.SOLI_RECU_ID),
    SOLI_QTD: toNumberOrNull(r.SOLI_QTD),
  }));

  // Reconstroi as reservas agrupando períodos e solicitações pelo RESE_ID.
  // Os CSVs não ligam reserva a ambiente; distribuímos os ambientes de forma
  // determinística para que o painel-grade (RF16) tenha dados para exibir.
  const ambientesAtivos = ambientes.filter((a) => a.AMBI_ST_ATIVO === 'S');
  const reservaIds = [
    ...new Set<number>([
      ...periodos.map((p) => p.PRES_RESE_ID),
      ...solicitacoes.map((s) => s.SOLI_RESE_ID),
    ]),
  ].sort((a, b) => a - b);

  const reservas: Reserva[] = reservaIds.map((id, i) => {
    const sols = solicitacoes.filter((s) => s.SOLI_RESE_ID === id);
    const temAmbiente = i % 4 !== 0; // ~75% com ambiente, 25% "local próprio"
    const ambiente = temAmbiente
      ? ambientesAtivos[i % ambientesAtivos.length]
      : null;
    return {
      RESE_ID: id,
      RESE_FINALIDADE: `Evento importado #${id}`,
      RESE_PARTICIPANTES: 10 + (i % 40),
      RESE_AMBI_ID: ambiente ? ambiente.AMBI_ID : null,
      RESE_COMPLEMENTO: ambiente ? null : 'Sala própria do setor',
      RESE_DISP_ID: null,
      RESE_UNID_ID: UNIDADE_PADRAO,
      RESE_SOLICITANTE: 'importado',
      RESE_SOLICITANTE_NOME: 'Dados importados',
      RESE_CANCELADA: false,
      RESE_DTHR_ALTERACAO: new Date().toISOString(),
      periodos: periodos.filter((p) => p.PRES_RESE_ID === id),
      solicitacoes: sols,
      snps: [],
    };
  });

  const config: Configuracao = {
    antecedenciaMinimaMin: 60, // RF09
    horarioMinGlobal: '07:00',
    horarioMaxGlobal: '21:00',
    margemToleranciaMin: 30, // RF11
    snpEndpoint: 'https://snp.mpf.mp.br/api/pedidos (simulado)',
    porUnidade: {},
  };

  const maxReseId = reservaIds.reduce((m, x) => Math.max(m, x), 0);
  const maxPresId = periodos.reduce((m, p) => Math.max(m, p.PRES_ID), 0);
  const maxSoliId = solicitacoes.reduce((m, s) => Math.max(m, s.SOLI_ID), 0);

  db = {
    unidades,
    ambientes,
    disposicoes,
    grupos,
    recursos,
    envolvidos,
    envolvidoAmbiente,
    envolvidoRecurso,
    vinculoRecurso,
    reservas,
    snps: [],
    config,
    seq: {
      reserva: maxReseId + 1,
      snp: 1,
      periodo: maxPresId + 1,
      solicitacao: maxSoliId + 1,
    },
  };
  return db;
}
