package br.mp.mpf.sisgares.comumaws.auth;

/** Token ausente, malformado ou expirado; mapeada para HTTP 401 (Req. 5.4). */
public class ExcecaoNaoAutenticado extends RuntimeException {
    public ExcecaoNaoAutenticado(String mensagem) {
        super(mensagem);
    }
}
