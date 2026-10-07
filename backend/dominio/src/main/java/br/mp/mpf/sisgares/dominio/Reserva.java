package br.mp.mpf.sisgares.dominio;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reserva (RESE) com seus Períodos (PRES) e Solicitações de Recurso (SOLI).
 *
 * <p>O construtor não valida regras de negócio (finalidade, participantes, períodos etc.): essas
 * verificações são RN1–RN9 e ficam no Validador_Reserva, que acumula todas as violações.
 *
 * @param id             RESE_ID; {@code null} para reserva nova
 * @param unidade        Unidade_Macro da reserva (ex.: "PR/CE")
 * @param solicitante    identificador do Solicitante (sub do Cognito)
 * @param ambienteId     AMBI_ID; {@code null} indica Local_Proprio
 * @param complemento    complemento do ambiente (obrigatório para Local_Proprio, RN2)
 * @param disposicaoId   DISP_ID opcional
 * @param finalidade     finalidade do evento
 * @param participantes  quantidade estimada de participantes; {@code null} quando ausente
 * @param periodos       Períodos [início, término) da reserva
 * @param solicitacoes   solicitações de Recursos
 * @param cancelada      indicador de cancelamento
 * @param canceladaEm    data/hora do cancelamento; {@code null} se não cancelada
 * @param alteradaEm     data/hora da última alteração; {@code null} se ainda não gravada
 * @param versao         número da versão (concorrência otimista); 0 para reserva nova
 */
public record Reserva(Long id, String unidade, String solicitante, Long ambienteId, String complemento,
                      Long disposicaoId, String finalidade, Integer participantes, List<Periodo> periodos,
                      List<Solicitacao> solicitacoes, boolean cancelada, LocalDateTime canceladaEm,
                      LocalDateTime alteradaEm, long versao) {

    public Reserva {
        Objects.requireNonNull(unidade, "unidade é obrigatória");
        if (versao < 0) {
            throw new IllegalArgumentException("versao não pode ser negativa");
        }
        // Cópias imutáveis; List.copyOf também rejeita elementos nulos
        periodos = periodos == null ? List.of() : List.copyOf(periodos);
        solicitacoes = solicitacoes == null ? List.of() : List.copyOf(solicitacoes);
    }

    /** Indica se a reserva é de Local_Proprio (sem Ambiente e sem verificação de conflito). */
    public boolean localProprio() {
        return ambienteId == null;
    }

    /** Primeiro início entre os Períodos (base de RN4, RN12 e RN13). */
    public Optional<LocalDateTime> primeiroInicio() {
        return periodos.stream().map(Periodo::inicio).min(Comparator.naturalOrder());
    }

    /** Último término entre os Períodos (base de RN13). */
    public Optional<LocalDateTime> ultimoTermino() {
        return periodos.stream().map(Periodo::termino).max(Comparator.naturalOrder());
    }
}
