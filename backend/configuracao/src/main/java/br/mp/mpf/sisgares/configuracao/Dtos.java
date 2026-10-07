package br.mp.mpf.sisgares.configuracao;

import br.mp.mpf.sisgares.dominio.Configuracao;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import java.util.TreeMap;

/** DTOs JSON de {@code /api/configuracao}. */
final class Dtos {

    private Dtos() {
    }

    /** Faixa horária no formato {@code HH:mm}. */
    record Faixa(String minimo, String maximo) {
        static Faixa de(Configuracao.FaixaHoraria f) {
            return new Faixa(f.minimo().toString(), f.maximo().toString());
        }
    }

    /** Corpo do PUT (whitelist: campos desconhecidos geram 422). */
    record EntradaConfiguracao(Integer antecedenciaMinutos, Faixa faixaGlobal,
                               Map<String, Faixa> faixasPorUnidade, String snpUrl) {
    }

    /** Resposta; {@code snpUrl} só é serializada para o Administrador. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SaidaConfiguracao(int antecedenciaMinutos, Faixa faixaGlobal,
                             Map<String, Faixa> faixasPorUnidade, String snpUrl) {

        static SaidaConfiguracao de(Configuracao c, String snpUrl) {
            Map<String, Faixa> faixas = new TreeMap<>();
            c.faixasPorUnidade().forEach((u, f) -> faixas.put(u, Faixa.de(f)));
            return new SaidaConfiguracao(c.antecedenciaMinutos(), Faixa.de(c.faixaGlobal()), faixas, snpUrl);
        }
    }
}
