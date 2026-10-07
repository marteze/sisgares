package br.mp.mpf.sisgares.comumaws.http;

/** Autorização negada pelo Verified Permissions (Cedar); mapeada para HTTP 403. */
public class ExcecaoAcessoNegado extends RuntimeException {

    public ExcecaoAcessoNegado(String mensagem) {
        super(mensagem);
    }
}
