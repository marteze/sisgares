package br.mp.mpf.sisgares.comumaws.http;

import br.mp.mpf.sisgares.dominio.Violacao;

import java.util.List;
import java.util.Objects;

/**
 * Corpo padrão de erro HTTP: {@code {"erros":[...],"correlationId":"..."}}.
 * Nunca contém stack trace, IDs internos ou dados pessoais.
 *
 * @param erros         lista de erros (ao menos um)
 * @param correlationId identificador para correlacionar com os logs
 */
public record RespostaErro(List<Erro> erros, String correlationId) {

    public RespostaErro {
        Objects.requireNonNull(correlationId, "correlationId é obrigatório");
        erros = List.copyOf(Objects.requireNonNull(erros, "erros é obrigatório"));
    }

    /**
     * Item de erro.
     *
     * @param codigo   código da regra (ex.: {@code RN5_...}, {@code ACESSO_NEGADO})
     * @param mensagem mensagem em português ao usuário
     * @param campo    campo relacionado ou {@code null}
     */
    public record Erro(String codigo, String mensagem, String campo) {

        public Erro {
            Objects.requireNonNull(codigo, "codigo é obrigatório");
            Objects.requireNonNull(mensagem, "mensagem é obrigatória");
        }

        /** Converte uma violação de domínio em item de erro. */
        public static Erro de(Violacao v) {
            return new Erro(v.codigo(), v.mensagem(), v.campo());
        }
    }

    /** Resposta com um único erro sem campo. */
    public static RespostaErro unico(String codigo, String mensagem, String correlationId) {
        return new RespostaErro(List.of(new Erro(codigo, mensagem, null)), correlationId);
    }
}
