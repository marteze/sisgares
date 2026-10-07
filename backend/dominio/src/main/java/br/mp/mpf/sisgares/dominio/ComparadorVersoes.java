package br.mp.mpf.sisgares.dominio;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Compara duas Versões_Reserva e lista os campos alterados com valor antigo e novo (Req. 13.4).
 *
 * <p>A comparação é feita sobre os valores já formatados: um campo só entra no resultado se a
 * representação formatada mudar. Períodos e solicitações são ordenados antes da formatação, de
 * modo que apenas a ordem diferente não gera Diferença; identificadores de Período são ignorados.
 */
public final class ComparadorVersoes {

    public static final String CAMPO_AMBIENTE = "Ambiente";
    public static final String CAMPO_COMPLEMENTO = "Complemento";
    public static final String CAMPO_DISPOSICAO = "Disposição";
    public static final String CAMPO_FINALIDADE = "Finalidade";
    public static final String CAMPO_PARTICIPANTES = "Participantes";
    public static final String CAMPO_PERIODOS = "Períodos";
    public static final String CAMPO_SOLICITACOES = "Recursos";
    public static final String CAMPO_CANCELAMENTO = "Cancelamento";

    /** Formato de data/hora exibido nos e-mails. */
    public static final DateTimeFormatter FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    static final String VAZIO = "";
    static final String LOCAL_PROPRIO = "Local próprio";
    static final String SEPARADOR_LISTA = "; ";

    private static final Comparator<Periodo> ORDEM_PERIODOS =
            Comparator.comparing(Periodo::inicio).thenComparing(Periodo::termino);
    private static final Comparator<Solicitacao> ORDEM_SOLICITACOES =
            Comparator.comparingLong(Solicitacao::recursoId).thenComparingInt(Solicitacao::quantidade);

    private ComparadorVersoes() {
    }

    /**
     * Retorna as diferenças entre a versão anterior e a nova, na ordem fixa dos campos.
     *
     * @param ant  versão anterior; {@code null} indica inclusão (retorna lista vazia)
     * @param nova nova versão (obrigatória)
     * @return lista imutável de diferenças; vazia se nada mudou
     */
    public static List<Diferenca> comparar(VersaoReserva ant, VersaoReserva nova) {
        Objects.requireNonNull(nova, "nova versão é obrigatória");
        if (ant == null) {
            // Inclusão: não há versão anterior para comparar
            return List.of();
        }
        Reserva a = ant.reserva();
        Reserva n = nova.reserva();
        List<Diferenca> diferencas = new ArrayList<>();
        adicionarSeDiferente(diferencas, CAMPO_AMBIENTE, a, n, ComparadorVersoes::formatarAmbiente);
        adicionarSeDiferente(diferencas, CAMPO_COMPLEMENTO, a, n, r -> texto(r.complemento()));
        adicionarSeDiferente(diferencas, CAMPO_DISPOSICAO, a, n, r -> texto(r.disposicaoId()));
        adicionarSeDiferente(diferencas, CAMPO_FINALIDADE, a, n, r -> texto(r.finalidade()));
        adicionarSeDiferente(diferencas, CAMPO_PARTICIPANTES, a, n, r -> texto(r.participantes()));
        adicionarSeDiferente(diferencas, CAMPO_PERIODOS, a, n, ComparadorVersoes::formatarPeriodos);
        adicionarSeDiferente(diferencas, CAMPO_SOLICITACOES, a, n, ComparadorVersoes::formatarSolicitacoes);
        adicionarSeDiferente(diferencas, CAMPO_CANCELAMENTO, a, n, ComparadorVersoes::formatarCancelamento);
        return List.copyOf(diferencas);
    }

    /** Adiciona a Diferença somente quando os valores formatados divergem. */
    private static void adicionarSeDiferente(List<Diferenca> destino, String campo, Reserva ant, Reserva nova,
                                             Function<Reserva, String> formatador) {
        String antigo = formatador.apply(ant);
        String novo = formatador.apply(nova);
        if (!antigo.equals(novo)) {
            destino.add(new Diferenca(campo, antigo, novo));
        }
    }

    private static String formatarAmbiente(Reserva r) {
        return r.ambienteId() == null ? LOCAL_PROPRIO : String.valueOf(r.ambienteId());
    }

    /** Lista de períodos ordenada, no formato "dd/MM/yyyy HH:mm a dd/MM/yyyy HH:mm". */
    private static String formatarPeriodos(Reserva r) {
        return r.periodos().stream()
                .sorted(ORDEM_PERIODOS)
                .map(p -> formatar(p.inicio()) + " a " + formatar(p.termino()))
                .collect(Collectors.joining(SEPARADOR_LISTA));
    }

    /** Lista de solicitações ordenada por recurso, no formato "Recurso {id}: {quantidade}". */
    private static String formatarSolicitacoes(Reserva r) {
        return r.solicitacoes().stream()
                .sorted(ORDEM_SOLICITACOES)
                .map(s -> "Recurso " + s.recursoId() + ": " + s.quantidade())
                .collect(Collectors.joining(SEPARADOR_LISTA));
    }

    private static String formatarCancelamento(Reserva r) {
        if (!r.cancelada()) {
            return "Não";
        }
        return r.canceladaEm() == null ? "Sim" : "Sim, em " + formatar(r.canceladaEm());
    }

    static String formatar(LocalDateTime dataHora) {
        return dataHora.format(FORMATO_DATA_HORA);
    }

    private static String texto(Object valor) {
        return valor == null ? VAZIO : String.valueOf(valor);
    }
}
