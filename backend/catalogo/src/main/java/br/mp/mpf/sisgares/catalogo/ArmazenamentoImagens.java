package br.mp.mpf.sisgares.catalogo;

/** Armazenamento das imagens de Disposição (bucket S3 privado em produção). */
public interface ArmazenamentoImagens {

    /**
     * Grava a imagem já validada.
     *
     * @param chave       chave do objeto no bucket
     * @param conteudo    bytes da imagem
     * @param contentType tipo MIME detectado pela assinatura
     */
    void gravar(String chave, byte[] conteudo, String contentType);
}
