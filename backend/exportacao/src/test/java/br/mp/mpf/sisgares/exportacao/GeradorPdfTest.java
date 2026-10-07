package br.mp.mpf.sisgares.exportacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.mp.mpf.sisgares.exportacao.ServicoExportacao.Grupo;
import br.mp.mpf.sisgares.exportacao.ServicoExportacao.Linha;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/**
 * Testes do {@link GeradorPdf}.
 *
 * <p><b>Validates: Requirements 17.2</b>
 */
class GeradorPdfTest {

    private static final LocalDate DIA = LocalDate.of(2025, 3, 10);

    @Test
    void geraPdfAgrupadoPorDataComCaracteresNaoSuportados() throws Exception {
        List<Linha> muitas = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            // Emoji e ideograma não existem na Helvetica: devem ser substituídos
            muitas.add(new Linha(i, DIA.atTime(8, 0), DIA.atTime(9, 0), "Auditório 🎤 会议",
                    "Reunião\ncom quebra " + "x".repeat(200), "sub-" + i, "Projetor (1)", i % 2 == 0));
        }
        byte[] pdf = GeradorPdf.gerar(List.of(new Grupo(DIA, muitas), new Grupo(DIA.plusDays(1), List.of())),
                "SISGARES - Reservas");

        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
            String texto = new PDFTextStripper().getText(doc);
            assertThat(texto).contains("10/03/2025", "11/03/2025", "Nenhuma reserva.", "Reunião", "CANCELADA");
        }
    }

    @Test
    void saneiaCaracteresForaDaFonte() {
        PDType1Font fonte = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        assertThat(GeradorPdf.sanear("Ação 🎤\tfim", fonte)).isEqualTo("Ação ? fim");
    }
}
