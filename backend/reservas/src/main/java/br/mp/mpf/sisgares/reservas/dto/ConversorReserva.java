package br.mp.mpf.sisgares.reservas.dto;

import br.mp.mpf.sisgares.comumaws.auth.Principal;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoAcessoNegado;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.Solicitacao;
import br.mp.mpf.sisgares.dominio.Status;

import java.util.List;
import java.util.Objects;

/**
 * Converte DTOs de entrada em {@link Reserva} do domínio e {@link Reserva} em {@link ReservaResposta}.
 *
 * <p>Sem regras de negócio: apenas tradução de formatos. Solicitante e Unidade_Macro vêm sempre do
 * {@link Principal} autenticado, nunca do corpo da requisição.
 */
public final class ConversorReserva {

    /** Quantidade assumida quando a solicitação de recurso não informa quantidade. */
    static final int QUANTIDADE_PADRAO = 1;

    private ConversorReserva() {
    }

    /**
     * Converte uma requisição em nova {@link Reserva} (sem id, versão 0).
     *
     * @see #paraDominio(ReservaRequisicao, Principal, Long, long)
     */
    public static Reserva paraDominio(ReservaRequisicao req, Principal principal) {
        return paraDominio(req, principal, null, 0L);
    }

    /**
     * Converte uma requisição em {@link Reserva}. Valida o DTO antes (Bean Validation), o que garante
     * que os IDs em String são numéricos.
     *
     * @param req       corpo da requisição
     * @param principal usuário autenticado (Solicitante e Unidade_Macro)
     * @param id        RESE_ID existente (alteração) ou {@code null} (criação)
     * @param versao    versão esperada para concorrência otimista (RN7); 0 para nova
     * @throws br.mp.mpf.sisgares.comumaws.http.ExcecaoValidacao se o DTO violar as restrições
     * @throws ExcecaoAcessoNegado se o usuário não tiver Unidade_Macro associada
     */
    public static Reserva paraDominio(ReservaRequisicao req, Principal principal, Long id, long versao) {
        ValidadorEntrada.validar(req);
        Objects.requireNonNull(principal, "principal é obrigatório");
        if (principal.unidade() == null || principal.unidade().isBlank()) {
            throw new ExcecaoAcessoNegado(
                    "Seu usuário não possui unidade associada. Procure o administrador do sistema.");
        }
        List<Periodo> periodos = req.periodos() == null ? List.of()
                : req.periodos().stream().map(ConversorReserva::paraPeriodo).toList();
        List<Solicitacao> solicitacoes = req.recursos() == null ? List.of()
                : req.recursos().stream().map(ConversorReserva::paraSolicitacao).toList();
        return new Reserva(id, principal.unidade(), principal.sub(), paraLong(req.ambienteId()),
                req.complemento(), paraLong(req.disposicaoId()), req.finalidade(), req.participantes(),
                periodos, solicitacoes, false, null, null, versao);
    }

    /** Converte um {@link PeriodoDto} em {@link Periodo} do domínio. */
    public static Periodo paraPeriodo(PeriodoDto dto) {
        return new Periodo(paraLong(dto.id()), dto.inicio(), dto.termino());
    }

    /**
     * Converte a resposta da API.
     *
     * @param reserva         Reserva do domínio
     * @param status          status calculado (RN13) pela {@code CalculadoraStatus}
     * @param solicitanteNome nome do Solicitante, ou {@code null} se o usuário não pode vê-lo (Req. 6.17)
     * @param pedidosSnp      pedidos no SNP simulado; {@code null} equivale a nenhum
     */
    public static ReservaResposta paraResposta(Reserva reserva, Status status, String solicitanteNome,
                                               List<ReservaResposta.PedidoSnp> pedidosSnp) {
        Objects.requireNonNull(reserva, "reserva é obrigatória");
        List<PeriodoDto> periodos = reserva.periodos().stream()
                .map(p -> new PeriodoDto(paraTexto(p.id()), p.inicio(), p.termino()))
                .toList();
        List<SolicitacaoDto> recursos = reserva.solicitacoes().stream()
                .map(s -> new SolicitacaoDto(String.valueOf(s.recursoId()), s.quantidade()))
                .toList();
        return new ReservaResposta(paraTexto(reserva.id()), status, solicitanteNome,
                paraTexto(reserva.ambienteId()), reserva.complemento(), paraTexto(reserva.disposicaoId()),
                reserva.finalidade(), reserva.participantes(), periodos, recursos, reserva.alteradaEm(),
                pedidosSnp);
    }

    private static Solicitacao paraSolicitacao(SolicitacaoDto dto) {
        int quantidade = dto.quantidade() == null ? QUANTIDADE_PADRAO : dto.quantidade();
        return new Solicitacao(Long.parseLong(dto.recursoId()), quantidade);
    }

    private static Long paraLong(String valor) {
        return valor == null ? null : Long.valueOf(valor);
    }

    private static String paraTexto(Long valor) {
        return valor == null ? null : String.valueOf(valor);
    }
}
