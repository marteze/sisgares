package br.mp.mpf.sisgares.comumaws.http;

/** RN7: condição otimista falhou ao salvar; mapeada para HTTP 409. */
public class ExcecaoConflitoConcorrente extends RuntimeException {

    public ExcecaoConflitoConcorrente(String mensagem) {
        super(mensagem);
    }

    public ExcecaoConflitoConcorrente(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
