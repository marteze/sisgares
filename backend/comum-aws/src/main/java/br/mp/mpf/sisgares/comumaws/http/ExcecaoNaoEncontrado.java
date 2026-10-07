package br.mp.mpf.sisgares.comumaws.http;

/** Recurso solicitado não existe; mapeada para HTTP 404. */
public class ExcecaoNaoEncontrado extends RuntimeException {

    public ExcecaoNaoEncontrado(String mensagem) {
        super(mensagem);
    }
}
