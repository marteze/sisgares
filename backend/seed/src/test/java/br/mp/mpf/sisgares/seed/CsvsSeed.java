package br.mp.mpf.sisgares.seed;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Carrega os CSVs fictícios de {@code data/} usados pelos testes do seed. Os arquivos ficam na
 * raiz do repositório; a busca sobe a partir do diretório do módulo até encontrar a pasta
 * {@code data}, de modo a funcionar tanto na worktree quanto em um checkout comum.
 */
final class CsvsSeed {

    private CsvsSeed() {
    }

    /** Mapa nome do arquivo CSV → conteúdo, como o seed recebe do bucket. */
    static Map<String, String> carregar() {
        Path dir = diretorioData();
        Map<String, String> csvs = new LinkedHashMap<>();
        try (Stream<Path> arquivos = Files.list(dir)) {
            for (Path arquivo : arquivos.filter(p -> p.toString().endsWith(".csv")).sorted().toList()) {
                csvs.put(arquivo.getFileName().toString(), Files.readString(arquivo, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return csvs;
    }

    /** Diretório {@code data/} procurado a partir do diretório de trabalho atual, subindo na árvore. */
    static Path diretorioData() {
        Path atual = Path.of("").toAbsolutePath();
        for (Path p = atual; p != null; p = p.getParent()) {
            Path candidato = p.resolve("data");
            if (Files.isDirectory(candidato) && Files.exists(candidato.resolve("dados-grupo-recurso.csv"))) {
                return candidato;
            }
        }
        throw new IllegalStateException("Pasta data/ com os CSVs fictícios não encontrada a partir de " + atual);
    }
}
