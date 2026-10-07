package br.mp.mpf.sisgares.configuracao;

import br.mp.mpf.sisgares.dominio.Configuracao;
import br.mp.mpf.sisgares.dominio.Configuracao.FaixaHoraria;
import br.mp.mpf.sisgares.dominio.ConstantesDominio;
import br.mp.mpf.sisgares.dominio.Violacao;
import java.net.URI;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Valida o corpo do {@code PUT /api/configuracao} acumulando todas as violações (Req. 8.1–8.5).
 */
final class ValidadorConfiguracao {

    static final String CONFIG_ANTECEDENCIA_INVALIDA = "CONFIG_ANTECEDENCIA_INVALIDA";
    static final String CONFIG_FAIXA_INVALIDA = "CONFIG_FAIXA_INVALIDA";
    static final String CONFIG_SNP_URL_INVALIDA = "CONFIG_SNP_URL_INVALIDA";

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    /** Resultado: configuração válida (quando não há violações) e a lista de violações. */
    record Resultado(Configuracao configuracao, String snpUrl, List<Violacao> violacoes) {
    }

    private final boolean modoLocal;

    /** @param modoLocal true quando {@code MODO_AUTH=mock}: aceita também {@code http://} no SNP */
    ValidadorConfiguracao(boolean modoLocal) {
        this.modoLocal = modoLocal;
    }

    Resultado validar(Dtos.EntradaConfiguracao entrada) {
        List<Violacao> violacoes = new ArrayList<>();
        if (entrada == null) {
            violacoes.add(Violacao.de(CONFIG_ANTECEDENCIA_INVALIDA,
                    "Informe o corpo da configuração."));
            return new Resultado(null, null, violacoes);
        }
        // Req. 8.1: antecedência inteira entre 0 e 10080
        Integer antecedencia = entrada.antecedenciaMinutos();
        if (antecedencia == null || antecedencia < 0
                || antecedencia > ConstantesDominio.ANTECEDENCIA_MAXIMA_MINUTOS) {
            violacoes.add(new Violacao(CONFIG_ANTECEDENCIA_INVALIDA,
                    "A antecedência mínima deve ser um inteiro entre 0 e "
                            + ConstantesDominio.ANTECEDENCIA_MAXIMA_MINUTOS + " minutos.",
                    "antecedenciaMinutos"));
        }
        // Req. 8.2 e 8.3: faixa global obrigatória; faixas por unidade opcionais; mínimo < máximo
        FaixaHoraria global = faixa(entrada.faixaGlobal(), "faixaGlobal", violacoes);
        Map<String, FaixaHoraria> porUnidade = new TreeMap<>();
        if (entrada.faixasPorUnidade() != null) {
            new TreeMap<>(entrada.faixasPorUnidade()).forEach((unidade, f) -> {
                String campo = "faixasPorUnidade." + unidade;
                if (unidade == null || unidade.isBlank()) {
                    violacoes.add(new Violacao(CONFIG_FAIXA_INVALIDA,
                            "Informe a unidade da faixa horária.", "faixasPorUnidade"));
                    return;
                }
                FaixaHoraria faixa = faixa(f, campo, violacoes);
                if (faixa != null) {
                    porUnidade.put(unidade.trim(), faixa);
                }
            });
        }
        // Req. 8.4 e 8.5: URL do SNP com https:// (http:// apenas no modo local)
        String snpUrl = entrada.snpUrl() == null || entrada.snpUrl().isBlank() ? null : entrada.snpUrl().trim();
        if (snpUrl != null && !urlValida(snpUrl)) {
            violacoes.add(new Violacao(CONFIG_SNP_URL_INVALIDA,
                    modoLocal
                            ? "A URL do SNP deve iniciar com https:// ou http:// e conter um host válido."
                            : "A URL do SNP deve iniciar com https:// e conter um host válido.",
                    "snpUrl"));
        }
        if (!violacoes.isEmpty()) {
            return new Resultado(null, null, violacoes);
        }
        return new Resultado(new Configuracao(antecedencia, global, porUnidade), snpUrl, List.of());
    }

    private boolean urlValida(String url) {
        String minusculo = url.toLowerCase();
        boolean prefixo = minusculo.startsWith("https://") || (modoLocal && minusculo.startsWith("http://"));
        if (!prefixo) {
            return false;
        }
        try {
            URI uri = new URI(url);
            return uri.getHost() != null && !uri.getHost().isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    /** Converte a faixa HH:mm; registra violação e devolve null quando inválida. */
    private static FaixaHoraria faixa(Dtos.Faixa f, String campo, List<Violacao> violacoes) {
        if (f == null) {
            violacoes.add(new Violacao(CONFIG_FAIXA_INVALIDA,
                    "Informe a faixa horária com mínimo e máximo no formato HH:mm.", campo));
            return null;
        }
        LocalTime minimo = horario(f.minimo());
        LocalTime maximo = horario(f.maximo());
        if (minimo == null || maximo == null) {
            violacoes.add(new Violacao(CONFIG_FAIXA_INVALIDA,
                    "Os horários da faixa devem estar no formato HH:mm (ex.: 07:00).", campo));
            return null;
        }
        if (!minimo.isBefore(maximo)) {
            violacoes.add(new Violacao(CONFIG_FAIXA_INVALIDA,
                    "O horário mínimo da faixa deve ser anterior ao horário máximo.", campo));
            return null;
        }
        return new FaixaHoraria(minimo, maximo);
    }

    private static LocalTime horario(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return LocalTime.parse(texto.trim(), HH_MM);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
