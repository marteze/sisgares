package br.mp.mpf.sisgares.seed;

import java.util.List;

/**
 * Relatório JSON da carga do seed (Requisitos 4.10, 4.11 e 4.16).
 *
 * @param unidade               Unidade_Macro criada
 * @param arquivos              resumo por arquivo CSV
 * @param usuarios              usuários fictícios gravados
 * @param reservas              reservas gravadas
 * @param reservasLocalProprio  reservas gravadas como Local_Proprio por falta de Ambiente livre
 * @param imagensComAlt         Imagens_Ícone com texto alternativo gravado
 * @param imagensSemAlt         Imagens_Ícone sem descrição em {@code descricoes.md}
 */
public record RelatorioCarga(
        String unidade,
        List<RelatorioArquivo> arquivos,
        int usuarios,
        int reservas,
        int reservasLocalProprio,
        int imagensComAlt,
        List<String> imagensSemAlt) {

    public RelatorioCarga {
        arquivos = List.copyOf(arquivos);
        imagensSemAlt = List.copyOf(imagensSemAlt);
    }

    /**
     * Resumo de um arquivo CSV.
     *
     * @param rejeicoes    linhas ignoradas (formato inválido, ID inexistente, arquivo ausente)
     * @param alertasIcone linhas gravadas com ícone padrão ({@code ICONE_NAO_ENCONTRADO}/{@code ICONE_AMBIGUO})
     */
    public record RelatorioArquivo(
            String arquivo,
            int lidas,
            int gravadas,
            int rejeitadas,
            List<Ocorrencia> rejeicoes,
            List<Ocorrencia> alertasIcone) {

        public RelatorioArquivo {
            rejeicoes = List.copyOf(rejeicoes);
            alertasIcone = List.copyOf(alertasIcone);
        }
    }

    /** Ocorrência por linha física do CSV (base 1); linha 0 indica o arquivo inteiro. */
    public record Ocorrencia(int linha, String motivo, String detalhe) {
    }
}
