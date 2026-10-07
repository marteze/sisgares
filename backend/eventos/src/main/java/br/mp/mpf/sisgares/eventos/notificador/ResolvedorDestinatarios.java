package br.mp.mpf.sisgares.eventos.notificador;

import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Setor;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.Solicitacao;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Resolve os destinatários de um Evento_Reserva (Req. 13.1, 13.2).
 *
 * <p>União, sem duplicatas, dos setores vinculados ao Ambiente (EAMB) e aos Recursos solicitados
 * (EREC); somente setores ativos. Cada setor recebe na lista alternativa, se houver; senão, em
 * ENVO_EMAIL. Setores sem endereço válido são ignorados. Saída ordenada por ID do setor.
 */
public final class ResolvedorDestinatarios {

    private final FonteSetores fonte;

    public ResolvedorDestinatarios(FonteSetores fonte) {
        this.fonte = Objects.requireNonNull(fonte, "fonte");
    }

    /**
     * Resolve os destinatários das reservas informadas (ex.: versão nova e anterior na alteração,
     * para que o setor do ambiente antigo também seja avisado). Reservas nulas são ignoradas.
     */
    public List<Destinatario> resolver(Collection<Reserva> reservas) {
        Set<Long> setores = new TreeSet<>();
        for (Reserva r : reservas) {
            if (r == null) {
                continue;
            }
            if (r.ambienteId() != null) {
                setores.addAll(fonte.setoresDoAmbiente(r.ambienteId()));
            }
            for (Solicitacao s : r.solicitacoes()) {
                setores.addAll(fonte.setoresDoRecurso(s.recursoId()));
            }
        }
        List<Destinatario> destinatarios = new ArrayList<>();
        for (Long envoId : setores) {
            fonte.setor(envoId)
                    .filter(Setor::ativo)
                    .flatMap(ResolvedorDestinatarios::enderecos)
                    .ifPresent(emails -> destinatarios.add(new Destinatario(envoId, emails)));
        }
        return List.copyOf(destinatarios);
    }

    /** Lista alternativa (não vazia) ou ENVO_EMAIL; vazio se nenhum endereço for válido. */
    static Optional<List<String>> enderecos(Setor setor) {
        List<String> alternativos = limpar(setor.emailsAlternativos());
        if (!alternativos.isEmpty()) {
            return Optional.of(alternativos);
        }
        List<String> principal = limpar(setor.email() == null ? List.of() : List.of(setor.email()));
        return principal.isEmpty() ? Optional.empty() : Optional.of(principal);
    }

    /** Remove brancos e duplicatas (sem diferenciar maiúsculas), preservando a ordem. */
    private static List<String> limpar(List<String> emails) {
        Set<String> vistos = new LinkedHashSet<>();
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
}
