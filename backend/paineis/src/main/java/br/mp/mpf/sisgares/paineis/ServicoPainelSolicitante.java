package br.mp.mpf.sisgares.paineis;

import br.mp.mpf.sisgares.comumaws.auth.Principal;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoValidacao;
import br.mp.mpf.sisgares.dominio.Ambiente;
import br.mp.mpf.sisgares.dominio.ArvoreAmbientes;
import br.mp.mpf.sisgares.dominio.CalculadoraGrade;
import br.mp.mpf.sisgares.dominio.CelulaGrade;
import br.mp.mpf.sisgares.dominio.ColunaGrade;
import br.mp.mpf.sisgares.dominio.Configuracao;
import br.mp.mpf.sisgares.dominio.Grade;
import br.mp.mpf.sisgares.dominio.ParametrosGrade;
import br.mp.mpf.sisgares.dominio.PeriodoOcupado;
import br.mp.mpf.sisgares.dominio.Violacao;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Painel do Solicitante (Req. 15.1, 15.4, 15.5, 15.11 e 6.17), sem dependência de AWS.
 *
 * <ul>
 *   <li>Estados das células calculados pela {@link CalculadoraGrade} do domínio, considerando
 *       os Períodos do Ambiente e dos Ambientes_Relacionados (mesma árvore).</li>
 *   <li>{@code reservaId} só aparece para o Solicitante dono da Reserva ou para o Administrador;
 *       aos demais a célula traz apenas o estado OCUPADO, sem Dados_Pessoais (6.17).</li>
 * </ul>
 */
public final class ServicoPainelSolicitante {

    /** Grupo do Cognito com acesso ao link de qualquer Reserva. */
    public static final String GRUPO_ADMINISTRADOR = "Administrador";
    /** Código das violações de parâmetro (HTTP 422). */
    public static final String PARAMETRO_INVALIDO = "PARAMETRO_INVALIDO";

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    /** Fonte de dados do painel; o Handler a implementa com os repositórios DynamoDB. */
    public interface FonteDados {

        /** Ambiente pelo id; vazio se não existir. */
        Optional<Ambiente> ambiente(long id);

        /** Todos os Ambientes da Unidade_Macro (para montar a árvore de RN6). */
        List<Ambiente> ambientesDaUnidade(String unidade);

        /** Períodos de reservas não canceladas da árvore {@code raizId} na janela informada. */
        List<PeriodoOcupado> ocupadosPorRaiz(long raizId, LocalDateTime ini, LocalDateTime fim);

        /** Sub do Solicitante da Reserva; vazio se a Reserva não existir. */
        Optional<String> solicitante(long reservaId);

        /** Configuração vigente (Antecedência_Mínima e Faixa_Horária). */
        Configuracao configuracao();
    }

    /** Parâmetros já validados da consulta. */
    public record Consulta(long ambienteId, LocalDate data, int colunas, boolean fds) {
    }

    /** Corpo da resposta: ids como texto, conforme o contrato do frontend. */
    public record RespostaPainel(String ambienteId, List<Coluna> colunas) {
    }

    /** Coluna da resposta: data {@code yyyy-MM-dd} e células. */
    public record Coluna(String data, List<Celula> celulas) {
    }

    /** Célula da resposta; {@code reservaId} omitido do JSON quando nulo. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Celula(String inicio, String estado, String reservaId) {
    }

    private final FonteDados fonte;
    private final CalculadoraGrade calculadora;

    public ServicoPainelSolicitante(FonteDados fonte, CalculadoraGrade calculadora) {
        this.fonte = Objects.requireNonNull(fonte, "fonte é obrigatória");
        this.calculadora = Objects.requireNonNull(calculadora, "calculadora é obrigatória");
    }

    /**
     * Valida os parâmetros de query, acumulando todas as violações (HTTP 422).
     *
     * @param params query string; {@code colunas} (padrão 7) e {@code fds} (padrão false) são opcionais
     * @throws ExcecaoValidacao com todas as violações encontradas
     */
    public static Consulta lerParametros(Map<String, String> params) {
        Map<String, String> p = params == null ? Map.of() : params;
        List<Violacao> violacoes = new ArrayList<>();

        Long ambienteId = null;
        String ambiente = aparar(p.get("ambiente"));
        try {
            ambienteId = ambiente == null ? null : Long.parseLong(ambiente);
        } catch (NumberFormatException e) {
            ambienteId = null;
        }
        if (ambienteId == null || ambienteId <= 0) {
            violacoes.add(new Violacao(PARAMETRO_INVALIDO,
                    "Informe o ambiente com um identificador numérico válido.", "ambiente"));
        }

        LocalDate data = null;
        String textoData = aparar(p.get("data"));
        try {
            data = textoData == null ? null : LocalDate.parse(textoData);
        } catch (DateTimeParseException e) {
            data = null;
        }
        if (data == null) {
            violacoes.add(new Violacao(PARAMETRO_INVALIDO,
                    "Informe a data de referência no formato AAAA-MM-DD.", "data"));
        }

        int colunas = ParametrosGrade.COLUNAS_PADRAO;
        String textoColunas = aparar(p.get("colunas"));
        if (textoColunas != null) {
            try {
                colunas = Integer.parseInt(textoColunas);
            } catch (NumberFormatException e) {
                colunas = -1;
            }
            if (colunas < ParametrosGrade.COLUNAS_MINIMO || colunas > ParametrosGrade.COLUNAS_MAXIMO) {
                violacoes.add(new Violacao(PARAMETRO_INVALIDO,
                        "Informe o número de colunas entre " + ParametrosGrade.COLUNAS_MINIMO
                                + " e " + ParametrosGrade.COLUNAS_MAXIMO + ".", "colunas"));
            }
        }

        boolean fds = false;
        String textoFds = aparar(p.get("fds"));
        if (textoFds != null) {
            if ("true".equalsIgnoreCase(textoFds)) {
                fds = true;
            } else if (!"false".equalsIgnoreCase(textoFds)) {
                violacoes.add(new Violacao(PARAMETRO_INVALIDO,
                        "Informe fds como true ou false.", "fds"));
            }
        }

        if (!violacoes.isEmpty()) {
            throw new ExcecaoValidacao(violacoes);
        }
        return new Consulta(ambienteId, data, colunas, fds);
    }

    /**
     * Calcula o painel para o usuário autenticado.
     *
     * @throws ExcecaoValidacao se o Ambiente não existir (422, campo {@code ambiente})
     */
    public RespostaPainel consultar(Consulta c, Principal usuario) {
        Objects.requireNonNull(c, "c é obrigatório");
        Objects.requireNonNull(usuario, "usuario é obrigatório");

        Ambiente ambiente = fonte.ambiente(c.ambienteId()).orElseThrow(() -> new ExcecaoValidacao(List.of(
                new Violacao(PARAMETRO_INVALIDO, "Ambiente não encontrado. Selecione um ambiente da lista.",
                        "ambiente"))));

        // Árvore da Unidade_Macro: base de Ambiente_Relacionado (RN6) e da raiz para o GSI1
        List<Ambiente> daUnidade = new ArrayList<>(fonte.ambientesDaUnidade(ambiente.unidade()));
        if (daUnidade.stream().noneMatch(a -> a.id() == ambiente.id())) {
            daUnidade.add(ambiente);
        }
        ArvoreAmbientes arvore = new ArvoreAmbientes(daUnidade);
        long raizId = arvore.raiz(ambiente.id()).id();

        ParametrosGrade parametros = new ParametrosGrade(ambiente.id(), ambiente.unidade(), c.data(),
                c.colunas(), c.fds());
        // Janela: do início da 1ª data ao fim da última; o repositório ainda amplia pela Margem
        LocalDateTime ini = c.data().atStartOfDay();
        LocalDateTime fim = ultimaData(parametros).plusDays(1).atStartOfDay();
        List<PeriodoOcupado> ocupados = fonte.ocupadosPorRaiz(raizId, ini, fim);

        Grade grade = calculadora.calcular(parametros, ocupados, arvore, fonte.configuracao());
        return montar(grade, usuario);
    }

    /** Converte a grade no DTO, exibindo reservaId só para dono ou Administrador (15.5, 6.17). */
    private RespostaPainel montar(Grade grade, Principal usuario) {
        boolean admin = usuario.pertenceA(GRUPO_ADMINISTRADOR);
        // Cache por requisição: cada Reserva ocupada é carregada uma única vez
        Map<Long, Boolean> visivel = new HashMap<>();
        List<Coluna> colunas = new ArrayList<>(grade.colunas().size());
        for (ColunaGrade coluna : grade.colunas()) {
            List<Celula> celulas = new ArrayList<>(coluna.celulas().size());
            for (CelulaGrade celula : coluna.celulas()) {
                String reservaId = null;
                if (celula.reservaId() != null) {
                    long id = celula.reservaId();
                    boolean pode = admin || visivel.computeIfAbsent(id,
                            r -> fonte.solicitante(r).map(usuario.sub()::equals).orElse(false));
                    reservaId = pode ? Long.toString(id) : null;
                }
                celulas.add(new Celula(HORA.format(celula.inicio()), celula.estado().name(), reservaId));
            }
            colunas.add(new Coluna(coluna.data().toString(), celulas));
        }
        return new RespostaPainel(Long.toString(grade.ambienteId()), colunas);
    }

    /** Última data exibida, com a mesma regra de fins de semana da CalculadoraGrade. */
    static LocalDate ultimaData(ParametrosGrade p) {
        LocalDate data = p.dataReferencia();
        LocalDate ultima = data;
        int contadas = 0;
        while (contadas < p.colunas()) {
            DayOfWeek dia = data.getDayOfWeek();
            if (p.fds() || (dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY)) {
                ultima = data;
                contadas++;
            }
            data = data.plusDays(1);
        }
        return ultima;
    }

    private static String aparar(String valor) {
        if (valor == null) {
            return null;
        }
        String s = valor.trim();
        return s.isEmpty() ? null : s;
    }
}
