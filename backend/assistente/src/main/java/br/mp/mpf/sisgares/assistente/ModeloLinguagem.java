package br.mp.mpf.sisgares.assistente;

/**
 * Porta para o modelo de linguagem (Bedrock em produção; implementação falsa nos testes).
 */
public interface ModeloLinguagem {

    /**
     * Envia as instruções e a mensagem ao modelo e devolve o texto gerado.
     *
     * @param instrucoes texto de sistema (formato da resposta)
     * @param mensagem   mensagem do usuário (descrição, data atual e catálogos)
     * @return texto bruto do modelo (pode não ser JSON válido)
     * @throws RuntimeException quando o modelo estiver indisponível
     */
    String gerar(String instrucoes, String mensagem);
}
