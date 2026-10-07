package br.mp.mpf.sisgares.comumaws.repositorio;

import br.mp.mpf.sisgares.comumaws.dynamo.NomesTabela;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Utilitários de conversão entre valores Java e {@link AttributeValue}.
 *
 * <p>Indicadores {@code *_ST_*} são gravados como "S"/"N" (Req. 3.6).
 */
final class Atributos {

    static final String SIM = "S";
    static final String NAO = "N";

    private Atributos() {
    }

    static AttributeValue s(String valor) {
        return AttributeValue.fromS(valor);
    }

    static AttributeValue n(long valor) {
        return AttributeValue.fromN(Long.toString(valor));
    }

    static AttributeValue st(boolean valor) {
        return s(valor ? SIM : NAO);
    }

    /** Chave primária {@code PK}/{@code SK}. */
    static Map<String, AttributeValue> chave(String pk, String sk) {
        Map<String, AttributeValue> chave = new HashMap<>();
        chave.put(NomesTabela.PK, s(pk));
        chave.put(NomesTabela.SK, s(sk));
        return chave;
    }

    /** Inclui o atributo texto somente quando não nulo e não vazio (DynamoDB rejeita string vazia em chave). */
    static void textoOpcional(Map<String, AttributeValue> item, String nome, String valor) {
        if (valor != null && !valor.isBlank()) {
            item.put(nome, s(valor));
        }
    }

    static void numeroOpcional(Map<String, AttributeValue> item, String nome, Long valor) {
        if (valor != null) {
            item.put(nome, n(valor));
        }
    }

    static void listaTextoOpcional(Map<String, AttributeValue> item, String nome, List<String> valores) {
        if (valores != null && !valores.isEmpty()) {
            item.put(nome, AttributeValue.fromL(valores.stream().map(Atributos::s).toList()));
        }
    }

    /** Lê atributo texto obrigatório; falha se ausente (item corrompido). */
    static String texto(Map<String, AttributeValue> item, String nome) {
        String valor = lerTexto(item, nome);
        if (valor == null) {
            throw new IllegalStateException("Atributo obrigatório ausente no item: " + nome);
        }
        return valor;
    }

    static String lerTexto(Map<String, AttributeValue> item, String nome) {
        AttributeValue valor = item.get(nome);
        return valor == null ? null : valor.s();
    }

    static long numero(Map<String, AttributeValue> item, String nome) {
        Long valor = lerNumero(item, nome);
        if (valor == null) {
            throw new IllegalStateException("Atributo obrigatório ausente no item: " + nome);
        }
        return valor;
    }

    static Long lerNumero(Map<String, AttributeValue> item, String nome) {
        AttributeValue valor = item.get(nome);
        return valor == null || valor.n() == null ? null : Long.valueOf(valor.n());
    }

    /** Indicador "S"/"N"; qualquer valor diferente de "S" é tratado como falso. */
    static boolean indicador(Map<String, AttributeValue> item, String nome) {
        return SIM.equals(lerTexto(item, nome));
    }

    static List<String> listaTexto(Map<String, AttributeValue> item, String nome) {
        AttributeValue valor = item.get(nome);
        if (valor == null || !valor.hasL()) {
            return List.of();
        }
        return valor.l().stream().map(AttributeValue::s).toList();
    }
}
