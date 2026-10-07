package br.mp.mpf.sisgares.dominio;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Calcula a grade do Painel do Solicitante (Req. 15.2, 15.3, 15.4, 15.6, 15.7, 15.8 e 15.11).
 *
 * <p>Cada célula [t, t+30) recebe o primeiro estado aplicável, nesta ordem:
 * <ol>
 *   <li>ULTRAPASSADO, se t &lt; agora;</li>
 *   <li>OCUPADO, se a célula cruza (sem margem) um ocupado do mesmo Ambiente ou de
 *       Ambiente_Relacionado, informando o reservaId;</li>
 *   <li>MARGEM, se {@link DetectorConflito#conflita} é verdadeiro sem cruzar o ocupado;</li>
 *   <li>SEM_ANTECEDENCIA, se t &lt; agora + Antecedência_Mínima;</li>
 *   <li>LIVRE, nos demais casos.</li>
 * </ol>
 * As regras de Margem e Antecedência_Mínima são as mesmas do Validador_Reserva (Property 13).
 */
public final class CalculadoraGrade {

    /** Duração de cada linha da grade. */
    public static final Duration PASSO = Duration.ofMinutes(30);

    private static final int PASSO_MINUTOS = (int) PASSO.toMinutes();

    private final Clock relogio;
    private final DetectorConflito detector = new DetectorConflito();

    /**
     * @param relogio relógio injetável; nos testes use {@code Clock.fixed(...)} (Req. 22.7)
     */
    public CalculadoraGrade(Clock relogio) {
        this.relogio = Objects.requireNonNull(relogio, "relogio é obrigatório");
    }

    /**
     * Calcula a grade.
     *
     * @param p        parâmetros (Ambiente, unidade, data de referência, colunas, fins de semana)
     * @param ocupados períodos de reservas não canceladas (podem incluir outros Ambientes; são filtrados)
     * @param arv      árvore de Ambientes para RN6
     * @param cfg      configuração com Antecedência_Mínima e Faixa_Horária
     * @return grade com uma coluna por data e uma célula por linha de 30 minutos
     */
    public Grade calcular(ParametrosGrade p, List<PeriodoOcupado> ocupados,
                          ArvoreAmbientes arv, Configuracao cfg) {
        Objects.requireNonNull(p, "p é obrigatório");
        Objects.requireNonNull(ocupados, "ocupados é obrigatório");
        Objects.requireNonNull(arv, "arv é obrigatório");
        Objects.requireNonNull(cfg, "cfg é obrigatório");

        // Instante atual sempre no fuso oficial, independentemente do fuso do Clock
        LocalDateTime agora = LocalDateTime.ofInstant(relogio.instant(), ConstantesDominio.ZONA);
        LocalDateTime limiteAntecedencia = agora.plusMinutes(cfg.antecedenciaMinutos());

        // Apenas ocupações do próprio Ambiente ou de Ambiente_Relacionado (RN5/RN6)
        List<PeriodoOcupado> relevantes = new ArrayList<>();
        for (PeriodoOcupado o : ocupados) {
            if (o.ambienteId() == p.ambienteId() || arv.saoRelacionados(p.ambienteId(), o.ambienteId())) {
                relevantes.add(o);
            }
        }

        List<LocalTime> linhas = linhas(cfg.faixaAplicavel(p.unidade()));
        List<ColunaGrade> colunas = new ArrayList<>(p.colunas());
        for (LocalDate data : datas(p)) {
            List<CelulaGrade> celulas = new ArrayList<>(linhas.size());
            for (LocalTime hora : linhas) {
                celulas.add(celula(data.atTime(hora), agora, limiteAntecedencia, relevantes));
            }
            colunas.add(new ColunaGrade(data, celulas));
        }
        return new Grade(p.ambienteId(), linhas, colunas);
    }

    /** Estado de uma célula segundo a ordem de precedência. */
    private CelulaGrade celula(LocalDateTime t, LocalDateTime agora, LocalDateTime limiteAntecedencia,
                               List<PeriodoOcupado> relevantes) {
        if (t.isBefore(agora)) {
            return new CelulaGrade(t, EstadoCelula.ULTRAPASSADO, null);
        }
        Periodo periodoCelula = Periodo.de(t, t.plus(PASSO));
        boolean margem = false;
        for (PeriodoOcupado o : relevantes) {
            if (cruza(periodoCelula, o.periodo())) {
                return new CelulaGrade(t, EstadoCelula.OCUPADO, o.reservaId());
            }
            // Continua o laço: um ocupado posterior pode cruzar a célula e tem precedência
            margem = margem || detector.conflita(periodoCelula, o.periodo());
        }
        if (margem) {
            return new CelulaGrade(t, EstadoCelula.MARGEM, null);
        }
        if (t.isBefore(limiteAntecedencia)) {
            return new CelulaGrade(t, EstadoCelula.SEM_ANTECEDENCIA, null);
        }
        return new CelulaGrade(t, EstadoCelula.LIVRE, null);
    }

    /** Sobreposição sem margem entre intervalos semiabertos [ini, fim). */
    private static boolean cruza(Periodo a, Periodo b) {
        return a.inicio().isBefore(b.termino()) && b.inicio().isBefore(a.termino());
    }

    /**
     * Horários iniciais das linhas: de {@code minimo} em passos de 30 minutos, enquanto o término
     * da célula não ultrapassar {@code maximo} (limites inclusos, como no Validador_Reserva).
     * Usa minutos do dia para não dar a volta à meia-noite.
     */
    private static List<LocalTime> linhas(Configuracao.FaixaHoraria faixa) {
        int minimo = faixa.minimo().getHour() * 60 + faixa.minimo().getMinute();
        int maximo = faixa.maximo().getHour() * 60 + faixa.maximo().getMinute();
        List<LocalTime> resultado = new ArrayList<>();
        for (int m = minimo; m + PASSO_MINUTOS <= maximo; m += PASSO_MINUTOS) {
            resultado.add(LocalTime.of(m / 60, m % 60));
        }
        return resultado;
    }

    /** Datas consecutivas a partir da referência, omitindo sábado e domingo se {@code fds} for falso. */
    private static List<LocalDate> datas(ParametrosGrade p) {
        List<LocalDate> resultado = new ArrayList<>(p.colunas());
        LocalDate data = p.dataReferencia();
        while (resultado.size() < p.colunas()) {
            if (p.fds() || !fimDeSemana(data)) {
                resultado.add(data);
            }
            data = data.plusDays(1);
        }
        return resultado;
    }

    private static boolean fimDeSemana(LocalDate data) {
        DayOfWeek dia = data.getDayOfWeek();
        return dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY;
    }
}
