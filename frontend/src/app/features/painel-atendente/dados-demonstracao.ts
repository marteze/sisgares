import { formatarDataIso, lerDataIso } from '../painel-solicitante/dados-demonstracao';
import { CardReserva, FiltroPainelAtendente, RespostaPainelAtendente } from './painel-atendente.service';

/**
 * Dados fictícios usados quando a API não responde (modo demonstração).
 * Nomes e números são inventados; nenhum dado real.
 */
const MODELOS: Omit<CardReserva, 'reservaId'>[] = [
  {
    inicio: '14:00', termino: '16:00', finalidade: 'Reunião de planejamento',
    solicitanteNome: 'Pessoa Solicitante A', ambiente: 'Auditório principal (demonstração)',
    recursos: [{ descricao: 'Projetor', quantidade: 1 }, { descricao: 'Café' }],
    pedidosSnp: [{ numero: 'SNP-0001', link: 'https://snp.exemplo.gov.br/pedidos/SNP-0001' }],
    cancelada: false,
  },
  {
    inicio: '09:00', termino: '10:30', finalidade: 'Treinamento interno',
    solicitanteNome: 'Pessoa Solicitante B', ambiente: 'Sala de treinamento (demonstração)',
    recursos: [{ descricao: 'Notebook', quantidade: 5 }],
    pedidosSnp: [],
    cancelada: false,
  },
  {
    inicio: '11:00', termino: '12:00', finalidade: 'Atendimento ao público',
    solicitanteNome: 'Pessoa Solicitante C', ambiente: 'Sala de reuniões 101 (demonstração)',
    recursos: [],
    pedidosSnp: [{ numero: 'SNP-0002', link: 'https://snp.exemplo.gov.br/pedidos/SNP-0002' }],
    cancelada: true,
  },
];

/** Gera colunas de datas consecutivas (omitindo fins de semana se `fds` for falso) com cards fictícios. */
export function gerarPainelAtendenteDemonstracao(filtro: FiltroPainelAtendente): RespostaPainelAtendente {
  const atual = lerDataIso(filtro.data);
  const colunas: RespostaPainelAtendente['colunas'] = [];
  let indice = 0;
  while (colunas.length < filtro.colunas) {
    const dia = atual.getDay();
    if (filtro.fds || (dia !== 0 && dia !== 6)) {
      const data = formatarDataIso(atual);
      const qtd = (indice % MODELOS.length) + 1;
      const cards = MODELOS.slice(0, qtd).map((m, i) => ({ ...m, reservaId: `DEMO-${data}-${i + 1}` }));
      colunas.push({ data, cards });
      indice++;
    }
    atual.setDate(atual.getDate() + 1);
  }
  return { colunas };
}
