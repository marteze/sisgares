package br.mp.mpf.sisgares.dominio.csv;

import java.util.List;

/**
 * Resultado da leitura de um CSV: registros convertidos e linhas rejeitadas.
 *
 * @param <T> tipo do registro de linha
 */
public record ResultadoLeitura<T>(List<T> registros, List<Rejeicao> rejeicoes) {

    public ResultadoLeitura {
        registros = List.copyOf(registros);
        rejeicoes = List.copyOf(rejeicoes);
    }
}
