package br.mp.mpf.sisgares.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.mp.mpf.sisgares.comumaws.http.ExcecaoValidacao;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Validação da imagem da Disposição por assinatura, tamanho e SVG sanitizado.
 *
 * <p>**Validates: Requirements 7.8**
 */
class ValidadorImagemTest {

    private static byte[] comPrefixo(byte[] prefixo, int tamanho) {
        byte[] dados = new byte[tamanho];
        System.arraycopy(prefixo, 0, dados, 0, prefixo.length);
        return dados;
    }

    private static String codigo(byte[] dados) {
        try {
            ValidadorImagem.validar(dados);
        } catch (ExcecaoValidacao e) {
            return e.violacoes().get(0).codigo();
        }
        throw new AssertionError("Esperava ExcecaoValidacao");
    }

    @Test
    void detectaPngEJpegPelaAssinatura() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        assertThat(ValidadorImagem.validar(comPrefixo(png, 100)).contentType()).isEqualTo("image/png");
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        assertThat(ValidadorImagem.validar(comPrefixo(jpeg, 100)).extensao()).isEqualTo("jpg");
    }

    @Test
    void rejeitaAcimaDe2MbEFormatoDesconhecido() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        assertThat(codigo(comPrefixo(jpeg, ValidadorImagem.LIMITE_BYTES + 1))).isEqualTo(ValidadorImagem.IMAGEM_GRANDE);
        assertThat(ValidadorImagem.validar(comPrefixo(jpeg, ValidadorImagem.LIMITE_BYTES))).isNotNull();
        assertThat(codigo("GIF89a".getBytes(StandardCharsets.US_ASCII))).isEqualTo(ValidadorImagem.IMAGEM_INVALIDA);
        assertThatThrownBy(() -> ValidadorImagem.validar(new byte[0])).isInstanceOf(ExcecaoValidacao.class);
    }

    @Test
    void svgComScriptOuEventoEhRejeitado() {
        String limpo = "<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"><circle r=\"1\"/></svg>";
        assertThat(ValidadorImagem.validar(limpo.getBytes(StandardCharsets.UTF_8)).contentType())
                .isEqualTo("image/svg+xml");
        assertThat(codigo("<svg><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(ValidadorImagem.SVG_INSEGURO);
        assertThat(codigo("<svg><rect ONCLICK = \"x()\"/></svg>".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(ValidadorImagem.SVG_INSEGURO);
        assertThat(codigo("<svg><a href=\"javascript:x()\"/></svg>".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(ValidadorImagem.SVG_INSEGURO);
    }
}
