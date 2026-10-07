package br.mp.mpf.sisgares.catalogo;

import br.mp.mpf.sisgares.comumaws.http.ExcecaoValidacao;
import br.mp.mpf.sisgares.dominio.Violacao;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Valida a imagem da Disposição (Req. 7.8): tipo detectado pela assinatura (magic bytes), nunca pela
 * extensão ou Content-Type informados; limite de 2 MB; SVG com script ou manipulador de evento rejeitado.
 */
public final class ValidadorImagem {

    public static final int LIMITE_BYTES = 2 * 1024 * 1024;
    public static final String IMAGEM_INVALIDA = "IMAGEM_INVALIDA";
    public static final String IMAGEM_GRANDE = "IMAGEM_GRANDE";
    public static final String SVG_INSEGURO = "SVG_INSEGURO";
    static final String CAMPO = "imagem";

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    /** Atributos {@code on*=} (onload, onclick...) e URLs {@code javascript:}. */
    private static final Pattern EVENTO = Pattern.compile("(?i)[\\s\"'/]on[a-z]+\\s*=");
    private static final Pattern JAVASCRIPT = Pattern.compile("(?i)javascript\\s*:");

    /** Tipo detectado: extensão do arquivo e Content-Type. */
    public record Tipo(String extensao, String contentType) {
    }

    private ValidadorImagem() {
    }

    /** Valida e devolve o tipo detectado; lança {@link ExcecaoValidacao} (422) se inválida. */
    public static Tipo validar(byte[] conteudo) {
        if (conteudo == null || conteudo.length == 0) {
            throw erro(IMAGEM_INVALIDA, "Envie uma imagem PNG, JPEG ou SVG.");
        }
        if (conteudo.length > LIMITE_BYTES) {
            throw erro(IMAGEM_GRANDE, "A imagem excede 2 MB. Reduza o arquivo e tente novamente.");
        }
        if (comecaCom(conteudo, PNG)) {
            return new Tipo("png", "image/png");
        }
        if (comecaCom(conteudo, JPEG)) {
            return new Tipo("jpg", "image/jpeg");
        }
        if (ehSvg(conteudo)) {
            String texto = new String(conteudo, StandardCharsets.UTF_8);
            if (texto.toLowerCase(Locale.ROOT).contains("<script")
                    || EVENTO.matcher(texto).find() || JAVASCRIPT.matcher(texto).find()) {
                throw erro(SVG_INSEGURO,
                        "O SVG contém script ou manipulador de evento. Remova-os e envie novamente.");
            }
            return new Tipo("svg", "image/svg+xml");
        }
        throw erro(IMAGEM_INVALIDA, "Formato não suportado. Envie uma imagem PNG, JPEG ou SVG.");
    }

    private static boolean comecaCom(byte[] dados, byte[] assinatura) {
        if (dados.length < assinatura.length) {
            return false;
        }
        for (int i = 0; i < assinatura.length; i++) {
            if (dados[i] != assinatura[i]) {
                return false;
            }
        }
        return true;
    }

    /** SVG: texto iniciado (após BOM/espaços) por {@code <?xml}, {@code <!--}, {@code <!DOCTYPE svg} ou {@code <svg}. */
    private static boolean ehSvg(byte[] dados) {
        String texto = new String(dados, StandardCharsets.UTF_8);
        if (texto.startsWith("\uFEFF")) {
            texto = texto.substring(1);
        }
        String inicio = texto.stripLeading().toLowerCase(Locale.ROOT);
        boolean prologoValido = inicio.startsWith("<svg") || inicio.startsWith("<?xml")
                || inicio.startsWith("<!--") || inicio.startsWith("<!doctype svg");
        return prologoValido && inicio.contains("<svg");
    }

    private static ExcecaoValidacao erro(String codigo, String mensagem) {
        return new ExcecaoValidacao(List.of(new Violacao(codigo, mensagem, CAMPO)));
    }
}
