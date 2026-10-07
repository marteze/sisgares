/** Entidades do catálogo administradas em `/cadastros/*` (rotas `/api/catalogo/{entidade}`). */
export type EntidadeCadastro = 'setores' | 'ambientes' | 'disposicoes' | 'grupos' | 'recursos';

export type TipoCampo = 'texto' | 'email' | 'numero' | 'lista-emails' | 'selecao' | 'booleano' | 'imagem';

/** Item retornado pela API (Respostas.java): ids em texto e flag `ativo`. */
export type ItemCadastro = Record<string, unknown> & { id: string; ativo: boolean };

export interface CampoCadastro {
  /** Nome do campo no DTO de requisição (usado também para casar erros 422 por `campo`). */
  nome: string;
  rotulo: string;
  tipo: TipoCampo;
  obrigatorio?: boolean;
  dica?: string;
  min?: number;
  max?: number;
  /** Entidade que fornece as opções de um campo `selecao`. */
  opcoes?: EntidadeCadastro;
  /** Rótulo da opção vazia em `selecao` (ex.: "Nenhum (raiz)"). */
  opcaoVazia?: string;
  /** Exibe o campo apenas quando a condição for verdadeira. */
  visivel?: (valores: Record<string, unknown>) => boolean;
}

export interface ColunaCadastro {
  titulo: string;
  valor: (item: ItemCadastro, rotuloDe: (entidade: EntidadeCadastro, id: unknown) => string) => string;
}

export interface ConfigCadastro {
  entidade: EntidadeCadastro;
  /** Título no plural (ex.: "Setores"). */
  titulo: string;
  /** Nome no singular, com artigo, usado em mensagens (ex.: "o setor"). */
  singular: string;
  campos: CampoCadastro[];
  colunas: ColunaCadastro[];
  /** Texto que identifica o item em tabelas, botões e opções de seleção. */
  rotulo: (item: ItemCadastro) => string;
  /** Converte a resposta da API em valores do formulário. */
  paraFormulario: (item: ItemCadastro) => Record<string, unknown>;
  /** Converte os valores do formulário no corpo de requisição (whitelist do backend). */
  paraRequisicao: (valores: Record<string, unknown>) => Record<string, unknown>;
  /** Monta o item da resposta a partir da requisição (somente no modo demonstração). */
  deRequisicao: (req: Record<string, unknown>) => Record<string, unknown>;
}

const texto = (v: unknown): string => (v === null || v === undefined ? '' : String(v)).trim();
const textoOuNulo = (v: unknown): string | null => texto(v) || null;
const idOuNulo = (v: unknown): number | null => (texto(v) ? Number(texto(v)) : null);
const numeroOuNulo = (v: unknown): number | null =>
  v === null || v === undefined || v === '' ? null : Number(v);
const idTexto = (v: unknown): string | undefined => (v === null || v === undefined ? undefined : String(v));

/** Separa e-mails digitados um por linha (ou por vírgula/ponto e vírgula). */
export function separarEmails(v: unknown): string[] {
  return texto(v)
    .split(/[\n,;]+/)
    .map((e) => e.trim())
    .filter(Boolean);
}

const CAMPO_DESCRICAO: CampoCadastro = { nome: 'descricao', rotulo: 'Descrição', tipo: 'texto', obrigatorio: true };
const CAMPO_UNIDADE: CampoCadastro = { nome: 'unidade', rotulo: 'Unidade', tipo: 'texto', dica: 'Sigla da unidade, ex.: PR-DF.' };

export const CONFIGS_CADASTRO: Record<EntidadeCadastro, ConfigCadastro> = {
  setores: {
    entidade: 'setores',
    titulo: 'Setores',
    singular: 'o setor',
    campos: [
      CAMPO_DESCRICAO,
      { ...CAMPO_UNIDADE, obrigatorio: true },
      { nome: 'email', rotulo: 'E-mail principal', tipo: 'email', obrigatorio: true },
      {
        nome: 'emailsAlternativos',
        rotulo: 'E-mails alternativos',
        tipo: 'lista-emails',
        dica: 'Um e-mail por linha.',
      },
    ],
    colunas: [
      { titulo: 'Descrição', valor: (i) => texto(i['descricao']) },
      { titulo: 'Unidade', valor: (i) => texto(i['unidade']) },
      { titulo: 'E-mail', valor: (i) => texto(i['email']) },
      { titulo: 'Alternativos', valor: (i) => ((i['emailsAlternativos'] as string[] | undefined) ?? []).join(', ') },
    ],
    rotulo: (i) => texto(i['descricao']),
    paraFormulario: (i) => ({
      descricao: i['descricao'] ?? '',
      unidade: i['unidade'] ?? '',
      email: i['email'] ?? '',
      emailsAlternativos: ((i['emailsAlternativos'] as string[] | undefined) ?? []).join('\n'),
    }),
    paraRequisicao: (v) => ({
      descricao: texto(v['descricao']),
      unidade: texto(v['unidade']),
      email: texto(v['email']),
      emailsAlternativos: separarEmails(v['emailsAlternativos']),
    }),
    deRequisicao: (r) => ({ ...r }),
  },
  ambientes: {
    entidade: 'ambientes',
    titulo: 'Ambientes',
    singular: 'o ambiente',
    campos: [
      CAMPO_DESCRICAO,
      { ...CAMPO_UNIDADE, obrigatorio: true },
      { nome: 'idPai', rotulo: 'Ambiente pai', tipo: 'selecao', opcoes: 'ambientes', opcaoVazia: 'Nenhum (ambiente raiz)' },
    ],
    colunas: [
      { titulo: 'Nome', valor: (i) => texto(i['nome']) },
      { titulo: 'Unidade', valor: (i) => texto(i['unidade']) },
      { titulo: 'Ambiente pai', valor: (i, rotuloDe) => (i['idPai'] ? rotuloDe('ambientes', i['idPai']) : 'Raiz') },
    ],
    rotulo: (i) => texto(i['nome']),
    paraFormulario: (i) => ({ descricao: i['nome'] ?? '', unidade: i['unidade'] ?? '', idPai: i['idPai'] ?? '' }),
    paraRequisicao: (v) => ({ descricao: texto(v['descricao']), unidade: texto(v['unidade']), idPai: idOuNulo(v['idPai']) }),
    deRequisicao: (r) => ({ nome: r['descricao'], unidade: r['unidade'], idPai: idTexto(r['idPai']) }),
  },
  disposicoes: {
    entidade: 'disposicoes',
    titulo: 'Disposições',
    singular: 'a disposição',
    campos: [
      CAMPO_DESCRICAO,
      { nome: 'alt', rotulo: 'Texto alternativo da imagem', tipo: 'texto', obrigatorio: true, dica: 'Descreva a disposição para leitores de tela.' },
      { nome: 'imagem', rotulo: 'Imagem (PNG, JPEG ou SVG, até 2 MB)', tipo: 'imagem' },
    ],
    colunas: [
      { titulo: 'Descrição', valor: (i) => texto(i['descricao']) },
      { titulo: 'Texto alternativo', valor: (i) => texto(i['alt']) },
      { titulo: 'Imagem', valor: (i) => (i['imagem'] ? 'Enviada' : 'Sem imagem') },
    ],
    rotulo: (i) => texto(i['descricao']),
    paraFormulario: (i) => ({ descricao: i['descricao'] ?? '', alt: i['alt'] ?? '' }),
    paraRequisicao: (v) => ({ descricao: texto(v['descricao']), alt: texto(v['alt']) }),
    deRequisicao: (r) => ({ ...r }),
  },
  grupos: {
    entidade: 'grupos',
    titulo: 'Grupos de recursos',
    singular: 'o grupo de recursos',
    campos: [
      CAMPO_DESCRICAO,
      { nome: 'ordem', rotulo: 'Ordem de exibição', tipo: 'numero', obrigatorio: true, min: 0, max: 9999 },
    ],
    colunas: [
      { titulo: 'Descrição', valor: (i) => texto(i['descricao']) },
      { titulo: 'Ordem', valor: (i) => texto(i['ordem']) },
    ],
    rotulo: (i) => texto(i['descricao']),
    paraFormulario: (i) => ({ descricao: i['descricao'] ?? '', ordem: i['ordem'] ?? null }),
    paraRequisicao: (v) => ({ descricao: texto(v['descricao']), ordem: numeroOuNulo(v['ordem']) }),
    deRequisicao: (r) => ({ ...r }),
  },
  recursos: {
    entidade: 'recursos',
    titulo: 'Recursos',
    singular: 'o recurso',
    campos: [
      CAMPO_DESCRICAO,
      { nome: 'grupoId', rotulo: 'Grupo de recursos', tipo: 'selecao', opcoes: 'grupos', obrigatorio: true },
      { nome: 'icone', rotulo: 'Ícone', tipo: 'texto', dica: 'Nome do arquivo do ícone, ex.: projetor.svg.' },
      { ...CAMPO_UNIDADE, dica: 'Deixe em branco para disponibilizar em todas as unidades.' },
      { nome: 'limitado', rotulo: 'Quantidade limitada', tipo: 'booleano' },
      {
        nome: 'disponibilidade',
        rotulo: 'Disponibilidade (quantidade)',
        tipo: 'numero',
        obrigatorio: true,
        min: 1,
        max: 9999,
        visivel: (v) => v['limitado'] === true,
      },
    ],
    colunas: [
      { titulo: 'Descrição', valor: (i) => texto(i['descricao']) },
      { titulo: 'Grupo', valor: (i, rotuloDe) => texto(i['grupoNome']) || rotuloDe('grupos', i['grupoId']) },
      { titulo: 'Unidade', valor: (i) => texto(i['unidade']) || 'Todas' },
      { titulo: 'Limitado', valor: (i) => (i['limitado'] ? `Sim (${texto(i['disponibilidade'])})` : 'Não') },
    ],
    rotulo: (i) => texto(i['descricao']),
    paraFormulario: (i) => ({
      descricao: i['descricao'] ?? '',
      grupoId: i['grupoId'] ?? '',
      icone: i['icone'] ?? '',
      unidade: i['unidade'] ?? '',
      limitado: i['limitado'] === true,
      disponibilidade: i['disponibilidade'] ?? null,
    }),
    paraRequisicao: (v) => ({
      descricao: texto(v['descricao']),
      grupoId: idOuNulo(v['grupoId']),
      icone: textoOuNulo(v['icone']),
      unidade: textoOuNulo(v['unidade']),
      limitado: v['limitado'] === true,
      disponibilidade: v['limitado'] === true ? numeroOuNulo(v['disponibilidade']) : null,
    }),
    deRequisicao: (r) => ({ ...r, grupoId: idTexto(r['grupoId']), unidade: r['unidade'] ?? undefined }),
  },
};

/** Dados fictícios para o modo demonstração (sem dados reais; domínio exemplo.gov.br). */
export const DADOS_DEMO_CADASTRO: Record<EntidadeCadastro, ItemCadastro[]> = {
  setores: [
    { id: '1', descricao: 'Tecnologia da Informação', unidade: 'PR-DF', email: 'ti@exemplo.gov.br', emailsAlternativos: ['suporte@exemplo.gov.br'], ativo: true },
    { id: '2', descricao: 'Comunicação', unidade: 'PR-DF', email: 'comunicacao@exemplo.gov.br', emailsAlternativos: [], ativo: true },
    { id: '3', descricao: 'Serviços Gerais', unidade: 'PR-SP', email: 'servicos@exemplo.gov.br', emailsAlternativos: [], ativo: false },
  ],
  ambientes: [
    { id: '10', nome: 'Edifício Sede', unidade: 'PR-DF', ativo: true },
    { id: '11', nome: 'Auditório', unidade: 'PR-DF', idPai: '10', ativo: true },
    { id: '12', nome: 'Sala de Reuniões 1', unidade: 'PR-DF', idPai: '10', ativo: true },
    { id: '13', nome: 'Sala antiga', unidade: 'PR-DF', idPai: '10', ativo: false },
  ],
  disposicoes: [
    { id: '20', descricao: 'Auditório', alt: 'Cadeiras enfileiradas voltadas para a mesa', ativo: true },
    { id: '21', descricao: 'Formato U', alt: 'Mesas em formato de U', ativo: true },
  ],
  grupos: [
    { id: '30', descricao: 'Audiovisual', ordem: 1, ativo: true },
    { id: '31', descricao: 'Informática', ordem: 2, ativo: true },
  ],
  recursos: [
    { id: '40', descricao: 'Projetor', grupoId: '30', grupoNome: 'Audiovisual', limitado: true, disponibilidade: 3, ativo: true },
    { id: '41', descricao: 'Notebook', grupoId: '31', grupoNome: 'Informática', limitado: true, disponibilidade: 5, ativo: true },
    { id: '42', descricao: 'Microfone sem fio', grupoId: '30', grupoNome: 'Audiovisual', limitado: false, unidade: 'PR-DF', ativo: true },
  ],
};
