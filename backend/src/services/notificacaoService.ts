// RF13: notificações automáticas por e-mail (simuladas).
// RF14: registro automático de pedidos no sistema nacional (SNP), simulado.
// RF15: ao alterar, a notificação destaca em HTML o que mudou.
import { getDb } from './dataStore.js';
import type { Envolvido, PedidoSNP, Reserva } from '../types.js';

export interface Notificacao {
  id: number;
  reservaId: number;
  setorId: number;
  setorEmail: string;
  emailsExtra: string[];
  assunto: string;
  corpoHtml: string;
  criadaEm: string;
  tipo: 'NOVA' | 'ALTERACAO' | 'CANCELAMENTO';
}

const historico: Notificacao[] = [];
let seqNotif = 1;

// Vínculos (setor + código de serviço SNP) para o ambiente e recursos da reserva.
interface VinculoSetor {
  setor: Envolvido;
  snpServico: string | null;
}

export function vinculosDaReserva(reserva: Reserva): VinculoSetor[] {
  const db = getDb();
  const porSetor = new Map<number, VinculoSetor>();

  const add = (setorId: number, snp: string | null) => {
    const setor = db.envolvidos.find((e) => e.ENVO_ID === setorId);
    if (!setor) return;
    const atual = porSetor.get(setorId);
    // Mantém o primeiro código de serviço não-nulo encontrado.
    porSetor.set(setorId, {
      setor,
      snpServico: atual?.snpServico ?? snp,
    });
  };

  // Recursos solicitados -> setores (RF07)
  for (const s of reserva.solicitacoes) {
    db.envolvidoRecurso
      .filter((er) => er.EREC_RECU_ID === s.SOLI_RECU_ID)
      .forEach((er) => add(er.EREC_ENVO_ID, er.EREC_SNP_SERVICO ?? null));
  }

  // Ambiente -> setores (RF03)
  if (reserva.RESE_AMBI_ID !== null) {
    db.envolvidoAmbiente
      .filter((ea) => ea.EAMB_AMBI_ID === reserva.RESE_AMBI_ID)
      .forEach((ea) => add(ea.EAMB_ENVO_ID, ea.EAMB_SNP_SERVICO ?? null));
  }

  return [...porSetor.values()];
}

export function setoresResponsaveis(reserva: Reserva): Envolvido[] {
  return vinculosDaReserva(reserva).map((v) => v.setor);
}

function emailsExtra(setor: Envolvido): string[] {
  if (!setor.ENVO_EMAILS_EXTRA) return [];
  return setor.ENVO_EMAILS_EXTRA.split(';')
    .map((e) => e.trim())
    .filter(Boolean);
}

// RF13: dispara notificação para cada setor envolvido.
// RF15: `diffHtml` (quando fornecido) destaca o que mudou.
export function notificarSetores(
  reserva: Reserva,
  tipo: Notificacao['tipo'] = 'NOVA',
  diffHtml?: string
): Notificacao[] {
  const vinculos = vinculosDaReserva(reserva);
  return vinculos.map(({ setor }) => {
    const assuntoPorTipo: Record<Notificacao['tipo'], string> = {
      NOVA: `[Reservas MPF] Nova reserva #${reserva.RESE_ID}`,
      ALTERACAO: `[Reservas MPF] Reserva #${reserva.RESE_ID} alterada`,
      CANCELAMENTO: `[Reservas MPF] Reserva #${reserva.RESE_ID} cancelada`,
    };
    const n: Notificacao = {
      id: seqNotif++,
      reservaId: reserva.RESE_ID,
      setorId: setor.ENVO_ID,
      setorEmail: setor.ENVO_EMAIL,
      emailsExtra: emailsExtra(setor),
      assunto: assuntoPorTipo[tipo],
      corpoHtml: montarCorpoHtml(reserva, setor.ENVO_DESC, diffHtml),
      criadaEm: new Date().toISOString(),
      tipo,
    };
    historico.push(n);
    console.log(`[NOTIFICACAO SIMULADA] -> ${n.setorEmail}: ${n.assunto}`);
    return n;
  });
}

function montarCorpoHtml(
  reserva: Reserva,
  setorNome: string,
  diffHtml?: string
): string {
  const recursos = reserva.solicitacoes.length;
  return [
    `<p>Setor <strong>${setorNome}</strong>, há uma demanda na reserva <strong>#${reserva.RESE_ID}</strong>.</p>`,
    `<p>Finalidade: ${reserva.RESE_FINALIDADE}<br/>Participantes: ${reserva.RESE_PARTICIPANTES}<br/>Recursos solicitados: ${recursos}</p>`,
    diffHtml
      ? `<div style="border-left:4px solid #b45309;padding-left:8px;margin-top:8px"><strong>Alterações:</strong>${diffHtml}</div>`
      : '',
  ].join('');
}

// RF14: registra pedidos SNP para os vínculos que têm código de serviço.
export function registrarPedidosSNP(reserva: Reserva): PedidoSNP[] {
  const db = getDb();
  const criados: PedidoSNP[] = [];
  for (const { setor, snpServico } of vinculosDaReserva(reserva)) {
    if (!snpServico) continue; // só registra quando há serviço no catálogo
    const codigo = `SNP-${new Date().getFullYear()}-${String(db.seq.snp).padStart(
      6,
      '0'
    )}`;
    const snp: PedidoSNP = {
      SNP_ID: db.seq.snp++,
      SNP_RESE_ID: reserva.RESE_ID,
      SNP_CODIGO: codigo,
      SNP_SERVICO: snpServico,
      SNP_SETOR_ID: setor.ENVO_ID,
      SNP_LINK: `${db.config.snpEndpoint.split(' ')[0]}/${codigo}`,
    };
    db.snps.push(snp);
    reserva.snps.push(snp);
    criados.push(snp);
    console.log(
      `[SNP SIMULADO] pedido ${codigo} (serviço ${snpServico}) para setor ${setor.ENVO_DESC}`
    );
  }
  return criados;
}

export function listarNotificacoes(): Notificacao[] {
  return historico;
}

export function notificacoesDoSetor(setorId: number): Notificacao[] {
  return historico.filter((n) => n.setorId === setorId);
}
