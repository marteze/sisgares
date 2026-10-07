package br.mp.mpf.sisgares.seed;

import br.mp.mpf.sisgares.comumaws.dynamo.ClienteDynamo;
import br.mp.mpf.sisgares.comumaws.http.Json;
import br.mp.mpf.sisgares.dominio.ConstantesDominio;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Executa o {@link ImportadorSeed} localmente (Requisito 1.7), lendo os CSVs de {@code data/} e
 * as Imagens_Ícone e {@code descricoes.md} de {@code imagens/} no disco, e gravando no DynamoDB
 * Local via {@link DestinoDynamo}.
 *
 * <p>Uso: {@code java -cp seed.jar br.mp.mpf.sisgares.seed.ExecutarSeedLocal <dirData> <dirImagens>}.
 * Tabela e endpoint vêm de {@code TABELA_SISGARES} (padrão {@code sisgares}) e
 * {@code DYNAMODB_ENDPOINT} (padrão {@code http://localhost:8000}).
 */
public final class ExecutarSeedLocal {

    private static final String TABELA_PADRAO = "sisgares";
    private static final String ENDPOINT_PADRAO = "http://localhost:8000";

    private ExecutarSeedLocal() {
    }

    public static void main(String[] args) throws IOException {
        Path dirData = Path.of(args.length > 0 ? args[0] : "data");
        Path dirImagens = Path.of(args.length > 1 ? args[1] : "imagens");

        Map<String, String> variaveis = new HashMap<>();
        variaveis.put(ClienteDynamo.VAR_TABELA, valorOuPadrao(ClienteDynamo.VAR_TABELA, TABELA_PADRAO));
        variaveis.put(ClienteDynamo.VAR_ENDPOINT, valorOuPadrao(ClienteDynamo.VAR_ENDPOINT, ENDPOINT_PADRAO));
        ClienteDynamo dynamo = ClienteDynamo.de(variaveis);
        Clock clock = ConstantesDominio.relogioPadrao();

        // CSVs indexados pelo nome do arquivo, como no bucket de seed
        Map<String, String> csvs = new HashMap<>();
        try (Stream<Path> arquivos = Files.list(dirData)) {
            for (Path arquivo : arquivos.filter(p -> p.toString().endsWith(".csv")).toList()) {
                csvs.put(arquivo.getFileName().toString(), Files.readString(arquivo, StandardCharsets.UTF_8));
            }
        }

        // Imagens com a pasta da categoria (ex.: icones-recurso/equip_001.png), como as chaves S3
        Path descricoes = dirImagens.resolve(HandlerSeed.CHAVE_DESCRICOES);
        List<String> imagens;
        try (Stream<Path> arquivos = Files.walk(dirImagens)) {
            imagens = arquivos.filter(Files::isRegularFile)
                    .filter(p -> !p.equals(descricoes))
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .map(p -> dirImagens.relativize(p).toString().replace('\\', '/'))
                    .toList();
        }
        String textoDescricoes = Files.exists(descricoes)
                ? Files.readString(descricoes, StandardCharsets.UTF_8) : null;

        DestinoDynamo destino = new DestinoDynamo(dynamo.cliente(), dynamo.nomeTabela(), clock);
        RelatorioCarga relatorio = new ImportadorSeed(destino, HandlerSeed.SNP_URL_PADRAO)
                .importar(csvs, imagens, textoDescricoes);
        // Relatório contém apenas contagens e motivos de rejeição (sem dados pessoais)
        System.out.println(Json.escrever(relatorio));
    }

    private static String valorOuPadrao(String variavel, String padrao) {
        String valor = System.getenv(variavel);
        return valor == null || valor.isBlank() ? padrao : valor.trim();
    }
}
