package br.mp.mpf.sisgares.dominio.csv;

/** Linha de {@code dados-envolvido-recurso.csv} (EREC_ID, EREC_ENVO_ID, EREC_RECU_ID). */
public record LinhaErec(long erecId, long erecEnvoId, long erecRecuId) {
}
