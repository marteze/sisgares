package br.mp.mpf.sisgares.exportacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.mp.mpf.sisgares.exportacao.ServicoExportacao.Grupo;
import br.mp.mpf.sisgares.exportacao.ServicoExportacao.Linha;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Testes do {@link GeradorCsv}.
 *
 * <p><b>Validates: Requirements 17.1</b>
 */
class GeradorCsvTest {

    private static final LocalDate DIA = LocalDate.of(2025, 3, 10);

    private static Linha linha(long id, String ambiente, String finalidade, String recursos) {
        return new Linha(id, DIA.atTime(9, 0), DIA.atTime(10, 30), ambiente, finalidade, "sub-" + id,
                recursos, false);
    }

    @Test
    void geraUmaLinhaPorPeriodoComCabecalhoESeparadorPontoEVirgula() {
        byte[] csv = GeradorCsv.gerar(List.of(
                new Grupo(DIA, List.of(linha(1, "Sala 1", "Reunião", "Projetor (2)"), linha(2, "Sala 2", "Aula", ""))),
                new Grupo(DIA.plusDays(1), List.of())));

        String texto = new String(csv, StandardCharsets.UTF_8);
        String[] linhas = texto.split("\r\n");
        assertThat(linhas).hasSize(3);
        assertThat(linhas[0]).isEqualTo(String.join(";", GeradorCsv.CABECALHO));
        assertThat(linhas[1]).isEqualTo("2025-03-10;09:00;2025-03-10T10:30;1;Sala 1;Reunião;sub-1;Projetor (2);nao");
    }

    @Test
    void roundTripPreservaSeparadorAspasEQuebrasDeLinha() {
        Linha l = linha(7, "Sala \"A\"; bloco 2", "Linha 1\nLinha 2\r\nfim", "Notebook (1), Projetor");
        byte[] csv = GeradorCsv.gerar(List.of(new Grupo(DIA, List.of(l))));

        List<List<String>> registros = ler(new String(csv, StandardCharsets.UTF_8));
        assertThat(registros).hasSize(2);
        assertThat(registros.get(1)).containsExactly("2025-03-10", "09:00", "2025-03-10T10:30", "7",
                "Sala \"A\"; bloco 2", "Linha 1\nLinha 2\r\nfim", "sub-7", "Notebook (1), Projetor", "nao");
    }

    @Test
    void prefixaCamposQueIniciamComCaracteresDeFormula() {
        assertThat(GeradorCsv.escapar("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(GeradorCsv.escapar("+1")).isEqualTo("'+1");
        assertThat(GeradorCsv.escapar("-2")).isEqualTo("'-2");
        assertThat(GeradorCsv.escapar("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(GeradorCsv.escapar("Reunião = ok")).isEqualTo("Reunião = ok");

        byte[] csv = GeradorCsv.gerar(List.of(new Grupo(DIA, List.of(linha(1, "Sala", "=cmd|' /C calc'!A0", "")))));
        assertThat(ler(new String(csv, StandardCharsets.UTF_8)).get(1).get(5)).isEqualTo("'=cmd|' /C calc'!A0");
    }

    /** Leitor mínimo de CSV com ";" e aspas (RFC 4180), só para o round-trip. */
    static List<List<String>> ler(String texto) {
        List<List<String>> registros = new ArrayList<>();
        List<String> atual = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean entreAspas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (entreAspas) {
                if (c == '"' && i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                    campo.append('"');
                    i++;
                } else if (c == '"') {
                    entreAspas = false;
                } else {
                    campo.append(c);
                }
            } else if (c == '"') {
                entreAspas = true;
            } else if (c == ';') {
                atual.add(campo.toString());
                campo.setLength(0);
            } else if (c == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') {
                atual.add(campo.toString());
                campo.setLength(0);
                registros.add(atual);
                atual = new ArrayList<>();
                i++;
            } else {
                campo.append(c);
            }
        }
        return registros;
    }
}
