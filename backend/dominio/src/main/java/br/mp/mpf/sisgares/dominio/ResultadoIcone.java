package br.mp.mpf.sisgares.dominio;

/**
 * Resultado da resolução de ícone a partir da referência do CSV.
 *
 * @param nomeResolvido      nome do arquivo de ícone escolhido (ou o padrão {@code indefinido_*})
 * @param referenciaOriginal referência exatamente como veio do CSV (preservada)
 * @param motivo             motivo da falha; {@code null} quando a resolução deu certo
 */
public record ResultadoIcone(String nomeResolvido, String referenciaOriginal, Motivo motivo) {

    /** Motivos de falha na resolução de ícone. */
    public enum Motivo {
        ICONE_NAO_ENCONTRADO,
        ICONE_AMBIGUO
    }

    /** Indica se a resolução encontrou exatamente um arquivo. */
    public boolean resolvido() {
        return motivo == null;
    }
}
