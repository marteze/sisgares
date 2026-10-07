package br.mp.mpf.sisgares.exportacao;

import br.mp.mpf.sisgares.exportacao.ServicoExportacao.Grupo;
import br.mp.mpf.sisgares.exportacao.ServicoExportacao.Linha;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Gera o PDF da exportação (Req. 17.2): Reservas agrupadas por data e ordenadas por início
 * (a ordenação já vem do {@link ServicoExportacao}).
 *
 * <p>Usa a fonte padrão Helvetica (WinAnsiEncoding): caracteres não suportados viram {@code ?}
 * e caracteres de controle viram espaço. Quebra linhas pela largura e pagina automaticamente.
 */
public final class GeradorPdf {

    private static final float MARGEM = 40f;
    private static final float TAMANHO_TITULO = 14f;
    private static final float TAMANHO_DATA = 12f;
    private static final float TAMANHO_TEXTO = 9f;
    private static final float ENTRELINHA = 1.35f;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private GeradorPdf() {
    }

    public static byte[] gerar(List<Grupo> grupos, String titulo) {
        try (PDDocument doc = new PDDocument()) {
            Escritor e = new Escritor(doc);
            try {
                e.escrever(titulo, e.negrito, TAMANHO_TITULO);
                e.espaco(TAMANHO_TEXTO);
                for (Grupo g : grupos) {
                    e.espaco(TAMANHO_TEXTO / 2);
                    e.escrever(DATA.format(g.data()), e.negrito, TAMANHO_DATA);
                    if (g.linhas().isEmpty()) {
                        e.escrever("Nenhuma reserva.", e.normal, TAMANHO_TEXTO);
                    }
                    for (Linha l : g.linhas()) {
                        String termino = l.termino().toLocalDate().equals(l.inicio().toLocalDate())
                                ? HORA.format(l.termino()) : DATA_HORA.format(l.termino());
                        StringBuilder sb = new StringBuilder()
                                .append(HORA.format(l.inicio())).append(" - ").append(termino)
                                .append(" | ").append(nulo(l.ambiente()))
                                .append(" | ").append(nulo(l.finalidade()))
                                .append(" | Solicitante: ").append(nulo(l.solicitante()));
                        if (l.recursos() != null && !l.recursos().isEmpty()) {
                            sb.append(" | Recursos: ").append(l.recursos());
                        }
                        if (l.cancelada()) {
                            sb.append(" | CANCELADA");
                        }
                        e.escrever(sb.toString(), e.normal, TAMANHO_TEXTO);
                    }
                }
            } finally {
                e.fechar();
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            doc.save(saida);
            return saida.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao gerar o PDF da exportação.", ex);
        }
    }

    /** Substitui caracteres que a fonte não codifica; controles viram espaço. */
    static String sanear(String texto, PDType1Font fonte) {
        StringBuilder sb = new StringBuilder(texto.length());
        texto.codePoints().forEach(cp -> {
            if (Character.isISOControl(cp)) {
                sb.append(' ');
                return;
            }
            String c = new String(Character.toChars(cp));
            try {
                fonte.encode(c);
                sb.append(c);
            } catch (IllegalArgumentException | IOException ex) {
                sb.append('?');
            }
        });
        return sb.toString();
    }

    private static String nulo(String s) {
        return s == null ? "" : s;
    }

    /** Estado de escrita: página corrente, posição vertical e quebra de linhas. */
    private static final class Escritor {
        final PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        final PDType1Font negrito = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final PDDocument doc;
        private final float largura = PDRectangle.A4.getWidth() - 2 * MARGEM;
        private PDPageContentStream conteudo;
        private float y;

        Escritor(PDDocument doc) throws IOException {
            this.doc = doc;
            novaPagina();
        }

        void novaPagina() throws IOException {
            fechar();
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            conteudo = new PDPageContentStream(doc, pagina);
            y = PDRectangle.A4.getHeight() - MARGEM;
        }

        void espaco(float altura) {
            y -= altura;
        }

        void escrever(String texto, PDType1Font fonte, float tamanho) throws IOException {
            for (String linha : quebrar(sanear(texto, fonte), fonte, tamanho)) {
                float altura = tamanho * ENTRELINHA;
                if (y - altura < MARGEM) {
                    novaPagina();
                }
                y -= altura;
                conteudo.beginText();
                conteudo.setFont(fonte, tamanho);
                conteudo.newLineAtOffset(MARGEM, y);
                conteudo.showText(linha);
                conteudo.endText();
            }
        }

        /** Quebra por palavras; palavras maiores que a largura são cortadas. */
        private List<String> quebrar(String texto, PDType1Font fonte, float tamanho) throws IOException {
            List<String> linhas = new ArrayList<>();
            StringBuilder atual = new StringBuilder();
            for (String palavra : texto.split(" ", -1)) {
                String candidata = atual.isEmpty() ? palavra : atual + " " + palavra;
                if (larguraDe(candidata, fonte, tamanho) <= largura) {
                    atual.setLength(0);
                    atual.append(candidata);
                    continue;
                }
                if (!atual.isEmpty()) {
                    linhas.add(atual.toString());
                    atual.setLength(0);
                }
                String resto = palavra;
                while (larguraDe(resto, fonte, tamanho) > largura) {
                    int corte = resto.length() - 1;
                    while (corte > 1 && larguraDe(resto.substring(0, corte), fonte, tamanho) > largura) {
                        corte--;
                    }
                    linhas.add(resto.substring(0, corte));
                    resto = resto.substring(corte);
                }
                atual.append(resto);
            }
            if (!atual.isEmpty() || linhas.isEmpty()) {
                linhas.add(atual.toString());
            }
            return linhas;
        }

        private static float larguraDe(String s, PDType1Font fonte, float tamanho) throws IOException {
            return fonte.getStringWidth(s) / 1000f * tamanho;
        }

        void fechar() throws IOException {
            if (conteudo != null) {
                conteudo.close();
                conteudo = null;
            }
        }
    }
}
