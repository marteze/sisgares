package br.mp.mpf.sisgares.dominio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Árvore imutável de {@link Ambiente}s, montada a partir de {@code idPai}.
 *
 * <p>Usada por DetectorConflito (RN6: Ambiente_Relacionado = ancestral ou descendente)
 * e pelo cadastro de Ambientes para validar a escolha do pai sem gerar ciclo (Req. 7.5).
 */
public final class ArvoreAmbientes {

    /** Pai proposto é o próprio Ambiente. */
    public static final String PAI_PROPRIO_AMBIENTE = "PAI_PROPRIO_AMBIENTE";
    /** Pai proposto é descendente do Ambiente (geraria ciclo). */
    public static final String PAI_DESCENDENTE = "PAI_DESCENDENTE";
    /** Pai proposto pertence a outra Unidade_Macro. */
    public static final String PAI_OUTRA_UNIDADE = "PAI_OUTRA_UNIDADE";
    /** Pai proposto não existe na árvore. */
    public static final String PAI_INEXISTENTE = "PAI_INEXISTENTE";

    private static final String CAMPO_PAI = "idPai";

    private final Map<Long, Ambiente> porId;
    private final Map<Long, List<Long>> filhos;

    /**
     * Monta a árvore.
     *
     * @param ambientes todos os Ambientes da(s) Unidade(s)_Macro consideradas
     * @throws IllegalArgumentException se houver IDs repetidos
     */
    public ArvoreAmbientes(Collection<Ambiente> ambientes) {
        Objects.requireNonNull(ambientes, "ambientes é obrigatório");
        Map<Long, Ambiente> mapa = new HashMap<>();
        Map<Long, List<Long>> mapaFilhos = new HashMap<>();
        for (Ambiente a : ambientes) {
            Objects.requireNonNull(a, "ambiente nulo na coleção");
            if (mapa.putIfAbsent(a.id(), a) != null) {
                throw new IllegalArgumentException("ambiente repetido: " + a.id());
            }
            if (a.idPai() != null) {
                mapaFilhos.computeIfAbsent(a.idPai(), k -> new ArrayList<>()).add(a.id());
            }
        }
        this.porId = Map.copyOf(mapa);
        Map<Long, List<Long>> copia = new HashMap<>();
        mapaFilhos.forEach((pai, lista) -> copia.put(pai, List.copyOf(lista)));
        this.filhos = Map.copyOf(copia);
    }

    /** Indica se o Ambiente existe na árvore. */
    public boolean contem(long id) {
        return porId.containsKey(id);
    }

    /**
     * Ambiente_Raiz do Ambiente informado (o próprio, se já for raiz).
     *
     * @throws IllegalArgumentException se o Ambiente não existir ou a cadeia de pais tiver ciclo
     */
    public Ambiente raiz(long id) {
        Ambiente atual = exigir(id);
        Set<Long> visitados = new HashSet<>();
        visitados.add(atual.id());
        // Sobe enquanto houver pai conhecido; pai ausente da árvore encerra a subida.
        while (atual.idPai() != null && porId.containsKey(atual.idPai())) {
            if (!visitados.add(atual.idPai())) {
                throw new IllegalArgumentException("ciclo na hierarquia de ambientes: " + id);
            }
            atual = porId.get(atual.idPai());
        }
        return atual;
    }

    /** IDs dos ancestrais (pai, avô, …), do mais próximo à raiz; vazio se desconhecido. */
    public Set<Long> ancestrais(long id) {
        Set<Long> resultado = new LinkedHashSet<>();
        Ambiente atual = porId.get(id);
        while (atual != null && atual.idPai() != null) {
            long pai = atual.idPai();
            if (pai == id || !resultado.add(pai)) {
                break; // proteção contra ciclo em dados inconsistentes
            }
            atual = porId.get(pai);
        }
        return Collections.unmodifiableSet(resultado);
    }

    /** IDs de todos os descendentes (filhos, netos, …), sem o próprio; vazio se não houver. */
    public Set<Long> descendentes(long id) {
        Set<Long> resultado = new LinkedHashSet<>();
        Deque<Long> pendentes = new ArrayDeque<>(filhos.getOrDefault(id, List.of()));
        while (!pendentes.isEmpty()) {
            long atual = pendentes.removeFirst();
            if (atual != id && resultado.add(atual)) {
                pendentes.addAll(filhos.getOrDefault(atual, List.of()));
            }
        }
        return Collections.unmodifiableSet(resultado);
    }

    /**
     * Ambientes_Relacionados (RN6): ancestrais e descendentes, sem o próprio Ambiente.
     */
    public Set<Long> relacionados(long id) {
        Set<Long> resultado = new LinkedHashSet<>(ancestrais(id));
        resultado.addAll(descendentes(id));
        resultado.remove(id);
        return Collections.unmodifiableSet(resultado);
    }

    /** Indica se {@code a} e {@code b} são Ambientes distintos e relacionados (ancestral/descendente). */
    public boolean saoRelacionados(long a, long b) {
        return a != b && (ancestrais(a).contains(b) || ancestrais(b).contains(a));
    }

    /**
     * Valida o pai proposto para um Ambiente (novo ou existente), acumulando as violações.
     *
     * @param id            ID do Ambiente sendo cadastrado/alterado
     * @param unidade       Unidade_Macro do Ambiente
     * @param idPaiProposto pai proposto; {@code null} torna o Ambiente raiz (sempre válido)
     * @return lista vazia se válido
     */
    public List<Violacao> validarPai(long id, String unidade, Long idPaiProposto) {
        Objects.requireNonNull(unidade, "unidade é obrigatória");
        if (idPaiProposto == null) {
            return List.of();
        }
        if (idPaiProposto == id) {
            return List.of(new Violacao(PAI_PROPRIO_AMBIENTE,
                    "O ambiente não pode ser pai de si mesmo. Escolha outro ambiente como pai.", CAMPO_PAI));
        }
        Ambiente pai = porId.get(idPaiProposto);
        if (pai == null) {
            return List.of(new Violacao(PAI_INEXISTENTE,
                    "O ambiente pai informado não existe. Escolha um ambiente cadastrado.", CAMPO_PAI));
        }
        List<Violacao> violacoes = new ArrayList<>();
        if (descendentes(id).contains(idPaiProposto)) {
            violacoes.add(new Violacao(PAI_DESCENDENTE,
                    "O ambiente pai não pode ser um descendente deste ambiente. Escolha um ambiente fora da subárvore.",
                    CAMPO_PAI));
        }
        if (!pai.unidade().equals(unidade)) {
            violacoes.add(new Violacao(PAI_OUTRA_UNIDADE,
                    "O ambiente pai deve pertencer à mesma unidade. Escolha um ambiente da mesma unidade.",
                    CAMPO_PAI));
        }
        return List.copyOf(violacoes);
    }

    /** Atalho de {@link #validarPai(long, String, Long)} para um Ambiente já montado. */
    public List<Violacao> validarPai(Ambiente ambiente, Long idPaiProposto) {
        Objects.requireNonNull(ambiente, "ambiente é obrigatório");
        return validarPai(ambiente.id(), ambiente.unidade(), idPaiProposto);
    }

    private Ambiente exigir(long id) {
        Ambiente a = porId.get(id);
        if (a == null) {
            throw new IllegalArgumentException("ambiente não encontrado: " + id);
        }
        return a;
    }
}
