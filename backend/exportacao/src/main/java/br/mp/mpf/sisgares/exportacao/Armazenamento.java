package br.mp.mpf.sisgares.exportacao;

import java.time.Instant;

/** Porta de gravação do arquivo exportado (implementada por {@link ArmazenamentoS3}). */
public interface Armazenamento {

    /** URL pré-assinada e instante de expiração. */
    record UrlAssinada(String url, Instant expiraEm) {
    }

    UrlAssinada gravar(String chave, byte[] conteudo, String contentType, String nomeArquivo);
}
