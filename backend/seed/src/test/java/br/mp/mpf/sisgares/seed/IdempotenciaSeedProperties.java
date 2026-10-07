package br.mp.mpf.sisgares.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

/**
 * Property 9 do seed: idempotência (Tarefa 12.2). Reexecutar a carga sobre o mesmo destino, ou
 * variar a ordem das Imagens_Ícone de entrada, não altera o conjunto final de itens gravados por
 * chave: as mesmas chaves recebem os mesmos valores, sem duplicação.
 *
 * <p><b>Validates: Requirements 4.7, 4.12</b>
 */
class IdempotenciaSeedProperties {

    private static final String SNP_URL = "https://snp-simulado.exemplo.gov.br/api";

    private static List<String> imagensQueResolvem() {
        List<String> imagens = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            imagens.add(String.format("icones-disposicao/disp_%03d_disposicao-ficticia.jpg", i));
        }
        for (String nome : List.of("serv_001", "serv_002", "serv_003", "estrut_001", "estrut_002",
                "equip_001", "equip_002", "equip_003", "equip_004", "equip_005", "equip_006",
                "equip_007", "equip_008", "equip_009", "equip_010", "equip_011")) {
            imagens.add("icones-recurso/" + nome + "_recurso-ficticio.png");
        }
        return imagens;
    }

    /**
     * Reexecutar o seed N vezes sobre o mesmo destino mantém exatamente os mesmos itens da primeira
     * execução (mesmas chaves e mesmos valores): regravar não duplica (Requisito 4.12).
     *
     * <p>// Feature: sisgares-reservas, Property 9: Idempotencia do seed
     */
    @Property(tries = 100)
    void reexecutarNaoDuplicaItens(@ForAll @IntRange(min = 2, max = 5) int execucoes) {
        Map<String, String> csvs = CsvsSeed.carregar();
        List<String> imagens = imagensQueResolvem();

        DestinoMemoria referencia = new DestinoMemoria();
        new ImportadorSeed(referencia, SNP_URL).importar(csvs, imagens, null);
        int itensAposPrimeira = referencia.itens.size();

        DestinoMemoria destino = new DestinoMemoria();
        for (int i = 0; i < execucoes; i++) {
            new ImportadorSeed(destino, SNP_URL).importar(csvs, imagens, null);
        }

        // Mesmo conjunto de chaves e valores da primeira execução, sem crescimento
        assertThat(destino.itens.keySet()).isEqualTo(referencia.itens.keySet());
        assertThat(destino.itens).isEqualTo(referencia.itens);
        assertThat(destino.itens).hasSize(itensAposPrimeira);
    }

    /**
     * A ordem das Imagens_Ícone recebidas não afeta o resultado: a carga é determinística
     * (ordenação interna por ID), produzindo o mesmo conjunto de itens (Requisito 4.7).
     *
     * <p>// Feature: sisgares-reservas, Property 9: Idempotencia do seed
     */
    @Property(tries = 100)
    void ordemDasImagensNaoAfetaResultado(@ForAll @LongRange(min = 0, max = 1_000_000) long semente) {
        Map<String, String> csvs = CsvsSeed.carregar();

        List<String> imagens = imagensQueResolvem();
        List<String> embaralhadas = new ArrayList<>(imagens);
        Collections.shuffle(embaralhadas, new Random(semente));

        DestinoMemoria destinoA = new DestinoMemoria();
        DestinoMemoria destinoB = new DestinoMemoria();
        new ImportadorSeed(destinoA, SNP_URL).importar(csvs, imagens, null);
        new ImportadorSeed(destinoB, SNP_URL).importar(csvs, embaralhadas, null);

        assertThat(destinoB.itens).isEqualTo(destinoA.itens);
    }
}
