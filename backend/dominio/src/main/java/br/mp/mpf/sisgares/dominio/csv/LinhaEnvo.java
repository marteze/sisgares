package br.mp.mpf.sisgares.dominio.csv;

/**
 * Linha de {@code dados-envolvido.csv} (ENVO_ID, ENVO_DESC, ENVO_EMAIL, ENVO_ST_ATIVO).
 * ENVO_EMAIL é dado pessoal/institucional: não registrar em logs sem MascaradorDados.
 */
public record LinhaEnvo(long envoId, String envoDesc, String envoEmail, String envoStAtivo) {
}
