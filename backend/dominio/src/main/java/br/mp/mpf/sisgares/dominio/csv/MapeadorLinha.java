package br.mp.mpf.sisgares.dominio.csv;

import java.util.List;
import java.util.Map;

/**
 * Converte entre uma linha de CSV (coluna → valor) e o registro tipado da entidade.
 *
 * @param <T> tipo do registro de linha
 */
public interface MapeadorLinha<T> {

    /** Nomes das colunas, exatamente como no cabeçalho do CSV e na ordem de escrita. */
    List<String> cabecalho();

    /**
     * Converte a linha em registro.
     *
     * @throws FormatoInvalidoException se algum campo estiver ausente ou fora do formato
     */
    T deLinha(Map<String, String> linha);

    /** Converte o registro em linha; valores null são escritos como campo vazio. */
    Map<String, String> paraLinha(T registro);
}
