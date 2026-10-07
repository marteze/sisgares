package br.mp.mpf.sisgares.comumaws.http;

import br.mp.mpf.sisgares.comumaws.auth.ExcecaoNaoAutenticado;
import br.mp.mpf.sisgares.dominio.CodigosRegra;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Converte exceções em {@link APIGatewayV2HTTPResponse} no formato
 * {@code {"erros":[...],"correlationId":"..."}}, sem stack trace.
 *
 * <ul>
 *   <li>{@link ExcecaoValidacao} → 422 com todas as violações</li>
 *   <li>{@link UnrecognizedPropertyException} → 422 {@code CAMPO_NAO_PERMITIDO} (whitelist)</li>
 *   <li>{@link JsonProcessingException} → 400 {@code SCHEMA_INVALIDO}</li>
 *   <li>{@link ExcecaoConflitoConcorrente} → 409 {@code RN7_CONFLITO_AO_SALVAR}</li>
 *   <li>{@link ExcecaoNaoEncontrado} → 404 {@code NAO_ENCONTRADO}</li>
 *   <li>{@link ExcecaoNaoAutenticado} → 401 {@code NAO_AUTENTICADO}</li>
 *   <li>{@link ExcecaoAcessoNegado} → 403 {@code ACESSO_NEGADO}</li>
 *   <li>qualquer outra → 500 {@code ERRO_INTERNO} (registrada no log)</li>
 * </ul>
 */
public final class MapeadorErros {

    public static final String SCHEMA_INVALIDO = "SCHEMA_INVALIDO";
    public static final String CAMPO_NAO_PERMITIDO = "CAMPO_NAO_PERMITIDO";
    public static final String NAO_AUTENTICADO = "NAO_AUTENTICADO";
    public static final String ACESSO_NEGADO = "ACESSO_NEGADO";
    public static final String NAO_ENCONTRADO = "NAO_ENCONTRADO";
    public static final String ERRO_INTERNO = "ERRO_INTERNO";

    private static final Map<String, String> CABECALHOS =
            Map.of("Content-Type", "application/json; charset=utf-8");

    private final LogEstruturado log;

    public MapeadorErros(LogEstruturado log) {
        this.log = Objects.requireNonNull(log, "log é obrigatório");
    }

    /** correlationId a partir do requestId do contexto; gera UUID quando ausente. */
    public static String correlationId(String requestId) {
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }

    /** correlationId a partir do requestId do evento HTTP API; gera UUID quando ausente. */
    public static String correlationId(APIGatewayV2HTTPEvent evento) {
        String requestId = evento == null || evento.getRequestContext() == null
                ? null
                : evento.getRequestContext().getRequestId();
        return correlationId(requestId);
    }

    /** Mapeia a exceção para a resposta HTTP correspondente. */
    public APIGatewayV2HTTPResponse mapear(Throwable erro, String correlationId) {
        String id = correlationId(correlationId);
        if (erro instanceof ExcecaoValidacao e) {
            List<RespostaErro.Erro> erros = e.violacoes().stream().map(RespostaErro.Erro::de).toList();
            return resposta(422, new RespostaErro(erros, id));
        }
        if (erro instanceof UnrecognizedPropertyException e) {
            return resposta(422, new RespostaErro(List.of(new RespostaErro.Erro(CAMPO_NAO_PERMITIDO,
                    "Campo não permitido na requisição. Remova-o e tente novamente.",
                    e.getPropertyName())), id));
        }
        if (erro instanceof JsonProcessingException) {
            return requisicaoInvalida(id);
        }
        if (erro instanceof ExcecaoConflitoConcorrente) {
            return resposta(409, RespostaErro.unico(CodigosRegra.RN7_CONFLITO_AO_SALVAR,
                    "RN7: a reserva foi alterada por outra pessoa. Recarregue e tente novamente.", id));
        }
        if (erro instanceof ExcecaoNaoEncontrado) {
            return resposta(404, RespostaErro.unico(NAO_ENCONTRADO,
                    "Registro não encontrado. Verifique o endereço e tente novamente.", id));
        }
        if (erro instanceof ExcecaoNaoAutenticado) {
            return naoAutenticado(id);
        }
        if (erro instanceof ExcecaoAcessoNegado) {
            return resposta(403, RespostaErro.unico(ACESSO_NEGADO,
                    "Você não tem permissão para esta operação.", id));
        }
        // Erro inesperado: detalhe só no log (mascarado, sem stack); resposta genérica
        log.erro(id, "Erro interno não tratado", erro);
        return resposta(500, RespostaErro.unico(ERRO_INTERNO,
                "Erro interno. Tente novamente e, se persistir, informe o correlationId ao suporte.", id));
    }

    /** 400: corpo fora do schema ou JSON malformado. */
    public static APIGatewayV2HTTPResponse requisicaoInvalida(String correlationId) {
        return resposta(400, RespostaErro.unico(SCHEMA_INVALIDO,
                "Requisição inválida. Corrija o corpo conforme o contrato da API.",
                correlationId(correlationId)));
    }

    /** 401: token ausente ou inválido. */
    public static APIGatewayV2HTTPResponse naoAutenticado(String correlationId) {
        return resposta(401, RespostaErro.unico(NAO_AUTENTICADO,
                "Sessão ausente ou expirada. Faça login novamente.", correlationId(correlationId)));
    }

    /** Monta a resposta JSON com Content-Type. */
    public static APIGatewayV2HTTPResponse resposta(int status, RespostaErro corpo) {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(status)
                .withHeaders(CABECALHOS)
                .withBody(Json.escrever(corpo))
                .withIsBase64Encoded(false)
                .build();
    }
}
