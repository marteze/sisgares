package br.mp.mpf.sisgares.reservas.dto;

import br.mp.mpf.sisgares.comumaws.http.ExcecaoValidacao;
import br.mp.mpf.sisgares.dominio.Violacao;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aplica Bean Validation aos DTOs de entrada (Req. 6.3) e lança {@link ExcecaoValidacao}
 * (HTTP 422) com uma {@link Violacao} por campo.
 *
 * <p>Usa {@link ParameterMessageInterpolator} (sem Jakarta EL), o que dispensa a dependência de
 * uma implementação de EL e reduz o cold start da Lambda. O {@link Validator} é thread-safe e
 * criado uma única vez.
 */
public final class ValidadorEntrada {

    /** Código devolvido para violações de Bean Validation. */
    public static final String CAMPO_INVALIDO = "CAMPO_INVALIDO";

    /** IDs numéricos trafegam como String; até 18 dígitos cabem em {@code long}. */
    public static final String REGEX_ID = "\\d{1,18}";
    public static final String MSG_ID = "Identificador inválido. Use apenas dígitos.";

    private static final Validator VALIDADOR;

    static {
        // A fábrica não é fechada: o Validator vive enquanto a Lambda estiver ativa
        ValidatorFactory fabrica = Validation.byProvider(HibernateValidator.class)
                .configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory();
        VALIDADOR = fabrica.getValidator();
    }

    private ValidadorEntrada() {
    }

    /**
     * Valida o DTO e lança {@link ExcecaoValidacao} se houver violações.
     *
     * @param dto objeto de entrada; {@code null} gera violação de corpo ausente
     * @return o próprio DTO, para encadeamento
     */
    public static <T> T validar(T dto) {
        List<Violacao> violacoes = violacoes(dto);
        if (!violacoes.isEmpty()) {
            throw new ExcecaoValidacao(violacoes);
        }
        return dto;
    }

    /** Retorna as violações do DTO (uma por campo, ordenadas pelo caminho), sem lançar exceção. */
    public static <T> List<Violacao> violacoes(T dto) {
        if (dto == null) {
            return List.of(Violacao.de(CAMPO_INVALIDO, "Corpo da requisição ausente."));
        }
        Set<ConstraintViolation<T>> encontradas = VALIDADOR.validate(dto);
        // Uma violação por campo: mantém a primeira mensagem em ordem determinística
        Map<String, Violacao> porCampo = new LinkedHashMap<>();
        encontradas.stream()
                .sorted(Comparator.comparing((ConstraintViolation<T> v) -> v.getPropertyPath().toString())
                        .thenComparing(ConstraintViolation::getMessage))
                .forEach(v -> {
                    String campo = v.getPropertyPath().toString();
                    porCampo.putIfAbsent(campo, new Violacao(CAMPO_INVALIDO, v.getMessage(), campo));
                });
        return List.copyOf(porCampo.values());
    }
}
