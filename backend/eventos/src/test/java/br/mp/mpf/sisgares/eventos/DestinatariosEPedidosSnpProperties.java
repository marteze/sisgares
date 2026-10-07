package br.mp.mpf.sisgares.eventos;

import static org.assertj.core.api.Assertions.assertThat;

import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Setor;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.Solicitacao;
import br.mp.mpf.sisgares.eventos.notificador.Destinatario;
import br.mp.mpf.sisgares.eventos.notificador.FonteSetores;
import br.mp.mpf.sisgares.eventos.notificador.ResolvedorDestinatarios;
import br.mp.mpf.sisgares.eventos.snp.ClienteSnp;
import br.mp.mpf.sisgares.eventos.snp.VinculoSnp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property 11: Destinatários e pedidos SNP.
 *
 * <ul>
 *   <li>Destinatários = união, sem duplicatas, dos setores ativos vinculados ao Ambiente (EAMB) e
 *       aos Recursos solicitados (EREC); cada setor recebe na lista alternativa, se houver, ou em
 *       ENVO_EMAIL; setores inativos ou sem endereço válido são ignorados (Req. 13.1).</li>
 *   <li>Pedidos_SNP só para vínculos com código de serviço (RN11); na alteração, apenas os
 *       vínculos novos; nunca os já registrados; sem duplicatas (Req. 14.1, 14.2, 14.4).</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 13.1, 14.1, 14.2, 14.4</b>
 */
class DestinatariosEPedidosSnpProperties {

    /**
     * // Feature: sisgares-reservas, Property 11: Destinatarios e pedidos SNP (destinatarios)
     */
    @Property(tries = 100)
    void destinatariosSaoUniaoDeSetoresAtivosComEnderecoValido(@ForAll("catalogos") Catalogo catalogo) {
        Reserva reserva = reserva(catalogo.ambienteId, catalogo.recursosSolicitados);
        List<Destinatario> destinatarios = new ResolvedorDestinatarios(catalogo).resolver(List.of(reserva));

        // Conjunto esperado: setores ativos com endereço, vinculados ao ambiente ou aos recursos
        Set<Long> esperados = new TreeSet<>();
        if (catalogo.ambienteId != null) {
            esperados.addAll(catalogo.setoresDoAmbiente(catalogo.ambienteId));
        }
        for (long recursoId : catalogo.recursosSolicitados) {
            esperados.addAll(catalogo.setoresDoRecurso(recursoId));
        }
        // Só setores ativos e com ao menos um endereço válido são destinatários
        esperados.removeIf(id -> !ehDestinatario(catalogo.setor(id).orElse(null)));

        List<Long> idsResolvidos = destinatarios.stream().map(Destinatario::envoId).toList();

        // Mesmos setores, sem duplicatas e ordenados por id
        assertThat(idsResolvidos).containsExactlyElementsOf(new ArrayList<>(esperados));
        assertThat(idsResolvidos).doesNotHaveDuplicates();

        // Cada destinatário recebe a lista alternativa, se houver; senão ENVO_EMAIL
        for (Destinatario d : destinatarios) {
            Setor setor = catalogo.setor(d.envoId()).orElseThrow();
            assertThat(setor.ativo()).isTrue();
            assertThat(d.emails()).isEqualTo(enderecosEsperados(setor));
            assertThat(d.emails()).doesNotHaveDuplicates().allSatisfy(e -> assertThat(e).isNotBlank());
        }
    }

    /**
     * Endereços efetivos esperados: lista alternativa (não vazia) ou ENVO_EMAIL; brancos e
     * duplicatas (sem diferenciar maiúsculas) removidos, preservando a ordem. Espelha a regra
     * do {@code ResolvedorDestinatarios} (Req. 13.1).
     */
    /** Setor ativo com ao menos um endereço válido. */
    private static boolean ehDestinatario(Setor setor) {
        return setor != null && setor.ativo() && !enderecosEsperados(setor).isEmpty();
    }

    private static List<String> enderecosEsperados(Setor setor) {
        List<String> alternativos = limpar(setor.emailsAlternativos());
        if (!alternativos.isEmpty()) {
            return alternativos;
        }
        return limpar(setor.email() == null ? List.of() : List.of(setor.email()));
    }

    private static List<String> limpar(List<String> emails) {
        Set<String> vistos = new java.util.LinkedHashSet<>();
        List<String> resultado = new ArrayList<>();
        for (String e : emails) {
            if (e == null || e.isBlank()) {
                continue;
            }
            String limpo = e.strip();
            if (vistos.add(limpo.toLowerCase(java.util.Locale.ROOT))) {
                resultado.add(limpo);
            }
        }
        return resultado;
    }

    /**
     * // Feature: sisgares-reservas, Property 11: Destinatarios e pedidos SNP (Pedidos_SNP)
     */
    @Property(tries = 100)
    void pedidosSnpSomenteParaVinculosComCodigoNovosENaoRegistrados(
            @ForAll("vinculos") List<VinculoSnp> atuais,
            @ForAll("vinculos") List<VinculoSnp> anteriores,
            @ForAll("chaves") Set<String> registrados) {

        List<VinculoSnp> selecionados = ClienteSnp.selecionar(atuais, anteriores, registrados);

        Set<String> chavesAnteriores = new HashSet<>();
        anteriores.forEach(v -> chavesAnteriores.add(v.chave()));
        Set<String> chavesSelecionadas = new HashSet<>();

        for (VinculoSnp v : selecionados) {
            // RN11: só vínculos com código de serviço
            assertThat(v.temCodigo()).isTrue();
            // Não registrado anteriormente
            assertThat(registrados).doesNotContain(v.chave());
            // Vínculo novo (ausente na versão anterior)
            assertThat(chavesAnteriores).doesNotContain(v.chave());
            // Sem duplicatas de chave
            assertThat(chavesSelecionadas.add(v.chave())).isTrue();
        }

        // Completude: todo vínculo com código, novo e não registrado precisa ser selecionado
        for (VinculoSnp v : atuais) {
            boolean elegivel = v.temCodigo()
                    && !chavesAnteriores.contains(v.chave())
                    && !registrados.contains(v.chave());
            if (elegivel) {
                assertThat(chavesSelecionadas).contains(v.chave());
            }
        }

        // Saída ordenada pela chave do vínculo
        assertThat(selecionados.stream().map(VinculoSnp::chave).toList())
                .isSorted();
    }

    // ---------- Geradores ----------

    @Provide
    Arbitrary<Catalogo> catalogos() {
        Arbitrary<List<Setor>> setores = setor().list().ofMinSize(1).ofMaxSize(6);
        Arbitrary<Long> ambiente = Arbitraries.oneOf(
                Arbitraries.just(null), Arbitraries.longs().between(1, 3).map(Long::valueOf));
        Arbitrary<List<Long>> recursos = Arbitraries.longs().between(1, 3)
                .map(Long::valueOf).list().ofMaxSize(3);
        return Combinators.combine(setores, ambiente, recursos).as(Catalogo::new);
    }

    private Arbitrary<Setor> setor() {
        Arbitrary<Long> id = Arbitraries.longs().between(1, 8);
        Arbitrary<Boolean> ativo = Arbitraries.of(true, false);
        // E-mail pode ser nulo/vazio para exercitar setores sem endereço
        Arbitrary<String> email = Arbitraries.of("s@exemplo.gov.br", "  ", "", null);
        // Setor rejeita elementos nulos na lista alternativa (List.copyOf); brancos exercitam limpar()
        Arbitrary<List<String>> alternativos = Arbitraries.of(
                "a@exemplo.gov.br", "b@exemplo.gov.br", "a@exemplo.gov.br", "", "  ")
                .list().ofMaxSize(3);
        return Combinators.combine(id, ativo, email, alternativos)
                .as((i, at, e, alt) -> new Setor(i, "Setor " + i, e, at, "PR/CE", alt));
    }

    @Provide
    Arbitrary<List<VinculoSnp>> vinculos() {
        Arbitrary<String> chave = Arbitraries.of("EAMB#1", "EAMB#2", "EREC#1", "EREC#2", "EREC#3");
        Arbitrary<String> codigo = Arbitraries.of("SRV-A", "SRV-B", null, "", "  ");
        Arbitrary<VinculoSnp> vinculo = Combinators.combine(chave, codigo)
                .as((c, cod) -> new VinculoSnp(c, cod, 1, 1));
        return vinculo.list().ofMaxSize(6);
    }

    @Provide
    Arbitrary<Set<String>> chaves() {
        return Arbitraries.of("EAMB#1", "EAMB#2", "EREC#1", "EREC#2", "EREC#3")
                .set().ofMaxSize(3);
    }

    // ---------- Catálogo em memória ----------

    private static Reserva reserva(Long ambienteId, List<Long> recursos) {
        LocalDateTime ini = LocalDateTime.of(2030, 3, 1, 14, 0);
        List<Solicitacao> solicitacoes = recursos.stream().map(r -> new Solicitacao(r, 1)).toList();
        return new Reserva(7L, "PR/CE", "sub-ficticio", ambienteId, ambienteId == null ? "Sala 1" : null,
                null, "Reunião", 10, List.of(Periodo.de(ini, ini.plusHours(1))), solicitacoes,
                false, null, null, 1);
    }

    /** Fonte de setores derivada da lista gerada; vínculos EAMB/EREC determinísticos por id. */
    private static final class Catalogo implements FonteSetores {
        final Map<Long, Setor> setores = new HashMap<>();
        final Long ambienteId;
        final List<Long> recursosSolicitados;

        Catalogo(List<Setor> lista, Long ambienteId, List<Long> recursos) {
            lista.forEach(s -> this.setores.putIfAbsent(s.id(), s));
            this.ambienteId = ambienteId;
            this.recursosSolicitados = List.copyOf(recursos);
        }

        /** Ambiente de id par vincula setores pares; ímpar, setores ímpares (determinístico). */
        @Override public List<Long> setoresDoAmbiente(long ambienteId) {
            return setores.keySet().stream().filter(id -> id % 2 == ambienteId % 2).sorted().toList();
        }

        /** Recurso vincula o setor de mesmo id, se existir. */
        @Override public List<Long> setoresDoRecurso(long recursoId) {
            return setores.containsKey(recursoId) ? List.of(recursoId) : List.of();
        }

        @Override public Optional<Setor> setor(long envoId) {
            return Optional.ofNullable(setores.get(envoId));
        }
    }
}
