package br.mp.mpf.sisgares.comumaws.http;

import br.mp.mpf.sisgares.dominio.Violacao;

import java.util.List;
import java.util.Objects;

/** Violações de domínio acumuladas; mapeada para HTTP 422 com todas as violações. */
public class ExcecaoValidacao extends RuntimeException {

    private final transient List<Violacao> violacoes;

    public ExcecaoValidacao(List<Violacao> violacoes) {
        super("Validação falhou", null, false, false);
        this.violacoes = List.copyOf(Objects.requireNonNull(violacoes, "violacoes é obrigatório"));
    }

    public List<Violacao> violacoes() {
        return violacoes;
    }
}
