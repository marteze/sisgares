package br.mp.mpf.sisgares.dominio;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Dados externos necessários ao Validador_Reserva, incluindo o {@link Clock} injetável (Req. 22.7).
 *
 * <p>Nos testes, use {@code Clock.fixed(instante, ConstantesDominio.ZONA)} para fixar o instante atual.
 *
 * @param relogio              relógio usado para obter o instante atual
 * @param configuracao         Antecedência_Mínima e Faixas_Horárias vigentes
 * @param ambientes            Ambientes conhecidos, por AMBI_ID
 * @param recursos             Recursos conhecidos, por RECU_ID
 * @param periodosOcupados     Períodos de outras Reservas não canceladas (RN5/RN6)
 * @param solicitacoesOcupadas quantidades de Recursos em outras Reservas não canceladas (RN8)
 * @param reservaAnterior      estado gravado da Reserva em alteração; {@code null} no cadastro (RN12)
 */
public record ContextoValidacao(Clock relogio, Configuracao configuracao, Map<Long, Ambiente> ambientes,
                                Map<Long, Recurso> recursos, List<PeriodoOcupado> periodosOcupados,
                                List<SolicitacaoOcupada> solicitacoesOcupadas, Reserva reservaAnterior) {

    public ContextoValidacao {
        Objects.requireNonNull(relogio, "relogio é obrigatório");
        Objects.requireNonNull(configuracao, "configuracao é obrigatória");
        ambientes = ambientes == null ? Map.of() : Map.copyOf(ambientes);
        recursos = recursos == null ? Map.of() : Map.copyOf(recursos);
        periodosOcupados = periodosOcupados == null ? List.of() : List.copyOf(periodosOcupados);
        solicitacoesOcupadas = solicitacoesOcupadas == null ? List.of() : List.copyOf(solicitacoesOcupadas);
    }

    /** Instante atual no fuso {@link ConstantesDominio#ZONA}, independente do fuso do relógio. */
    public LocalDateTime agora() {
        return LocalDateTime.ofInstant(relogio.instant(), ConstantesDominio.ZONA);
    }

    /** Indica se a validação é de uma alteração (existe Reserva gravada anteriormente). */
    public boolean alteracao() {
        return reservaAnterior != null;
    }

    /** Ambiente pelo identificador, se conhecido. */
    public Optional<Ambiente> ambiente(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(ambientes.get(id));
    }

    /** Recurso pelo identificador, se conhecido. */
    public Optional<Recurso> recurso(long id) {
        return Optional.ofNullable(recursos.get(id));
    }
}
