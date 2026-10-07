package br.mp.mpf.sisgares.paineis;

import br.mp.mpf.sisgares.comumaws.auth.Principal;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoAcessoNegado;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoValidacao;
import br.mp.mpf.sisgares.dominio.ParametrosGrade;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.Solicitacao;
import br.mp.mpf.sisgares.dominio.Violacao;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Painel do Atendente (Req. 16.2–16.6), sem dependência de AWS.
 *
 * <ul>
 *   <li>Setor_Atendente: somente Reservas que envolvem o seu Setor_Envolvido (GSI2, 16.4).</li>
 *   <li>Administrador: todas as Reservas da sua Unidade_Macro (GSI3, 16.5). Se o usuário tiver os
 *       dois perfis, prevalece o de Administrador (visão mais ampla).</li>
 *   <li>Demais perfis: 403.</li>
 * </ul>
 *
 * <p>Cada coluna (data) traz um card por Período que inicia naquela data, ordenado por início (16.2),
 * com Recursos, Pedidos_SNP registrados e o indicador {@code cancelada} (16.3, 16.6).
 *
 * <p><b>Nome do Solicitante:</b> a Reserva persiste apenas o {@code sub} do Cognito; não há nome
 * gravado na tabela. Por isso {@code solicitanteNome} recebe o {@code sub} até que exista uma fonte
 * de nomes (ex.: cadastro de usuários ou consulta ao Cognito).
 */
public final class ServicoPainelAtendente {

    public static final String GRUPO_ATENDENTE = "Setor_Atendente";
    public static final String GRUPO_ADMINISTRADOR = ServicoPainelSolicitante.GRUPO_ADMINISTRADOR;
    public static final String PARAMETRO_INVALIDO = ServicoPainelSolicitante.PARAMETRO_INVALIDO;

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    /** Fonte de dados do painel; o Handler a implementa com os repositórios DynamoDB. */
    public interface FonteDados {
        /** Reservas com Período iniciando em {@code [de, ate]} que envolvem o Setor (GSI2). */
        List<Reserva> porSetor(long envoId, LocalDateTime de, LocalDateTime ate);

        /** Reservas da Unidade_Macro com Período iniciando em {@code [de, ate]} (GSI3). */
        List<Reserva> porUnidade(String unidade, LocalDateTime de, LocalDateTime ate);

        /** Descrição do Ambiente no catálogo; vazio se não existir. */
        Optional<String> descricaoAmbiente(long ambienteId);

        /** Descrição do Recurso no catálogo; vazio se não existir. */
        Optional<String> descricaoRecurso(long recursoId);

        /** Pedidos_SNP com status REGISTRADO da Reserva ({@code PK=RESE#id}, {@code SK begins_with SNP#}). */
        List<PedidoSnpCard> pedidosSnp(long reservaId);
    }

    /** Parâmetros já validados. */
    public record Consulta(LocalDate data, int colunas, boolean fds) {
    }

    public record RespostaPainel(List<Coluna> colunas) {
    }

    public record Coluna(String data, List<Card> cards) {
    }

    /** Card de um Período (contrato {@code CardReserva} do frontend). */
    public record Card(String reservaId, String inicio, String termino, String finalidade,
                       String solicitanteNome, String ambiente, List<RecursoCard> recursos,
                       List<PedidoSnpCard> pedidosSnp, boolean cancelada) {
    }

    /** Recurso do card; {@code quantidade} omitida do JSON quando não houver contagem. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecursoCard(String descricao, Integer quantidade) {
    }

    public record PedidoSnpCard(String numero, String link) {
    }

    private final FonteDados fonte;

    public ServicoPainelAtendente(FonteDados fonte) {
        this.fonte = Objects.requireNonNull(fonte, "fonte é obrigatória");
    }

    /**
     * Valida os parâmetros, acumulando todas as violações (HTTP 422).
     * {@code data} é obrigatória; {@code colunas} (padrão 7, de 1 a 14) e {@code fds} (padrão false) são opcionais.
     */
    public static Consulta lerParametros(Map<String, String> params) {
        Map<String, String> p = params == null ? Map.of() : params;
        List<Violacao> violacoes = new ArrayList<>();

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
                violacoes.add(new Violacao(PARAMETRO_INVALIDO, "Informe fds como true ou false.", "fds"));
            }
        }

        if (!violacoes.isEmpty()) {
            throw new ExcecaoValidacao(violacoes);
        }
        return new Consulta(data, colunas, fds);
    }

    /**
     * Monta o painel conforme o perfil do usuário.
     *
     * @throws ExcecaoAcessoNegado (403) se o perfil não for Setor_Atendente/Administrador,
     *                             ou se faltar o setor (Atendente) ou a unidade (Admin)
     */
    public RespostaPainel consultar(Consulta c, Principal usuario) {
        Objects.requireNonNull(c, "c é obrigatório");
        Objects.requireNonNull(usuario, "usuario é obrigatório");

        List<LocalDate> datas = datas(c);
        LocalDateTime de = datas.get(0).atStartOfDay();
        LocalDateTime ate = datas.get(datas.size() - 1).atTime(LocalTime.MAX);

        List<Reserva> reservas;
        if (usuario.pertenceA(GRUPO_ADMINISTRADOR)) {
            String unidade = aparar(usuario.unidade());
            if (unidade == null) {
                throw new ExcecaoAcessoNegado("Administrador sem Unidade_Macro.");
            }
            reservas = fonte.porUnidade(unidade, de, ate);
        } else if (usuario.pertenceA(GRUPO_ATENDENTE)) {
            reservas = fonte.porSetor(setor(usuario), de, ate);
        } else {
            throw new ExcecaoAcessoNegado("Perfil sem acesso ao Painel do Atendente.");
        }
        return montar(datas, reservas);
    }

    /** Setor_Envolvido numérico do Principal; ausente ou inválido resulta em 403. */
    private static long setor(Principal usuario) {
        String texto = aparar(usuario.setor());
        if (texto != null) {
            try {
                return Long.parseLong(texto);
            } catch (NumberFormatException e) {
                // cai no 403 abaixo
            }
        }
        throw new ExcecaoAcessoNegado("Setor_Atendente sem Setor_Envolvido.");
    }

    private RespostaPainel montar(List<LocalDate> datas, List<Reserva> reservas) {
        // Uma lista de cards por data exibida; datas fora das colunas (ex.: fim de semana oculto) são ignoradas
        Map<LocalDate, List<CardOrdenado>> porData = new LinkedHashMap<>();
        datas.forEach(d -> porData.put(d, new ArrayList<>()));

        // Caches por requisição para evitar leituras repetidas do catálogo
        Map<Long, String> ambientes = new HashMap<>();
        Map<Long, String> recursos = new HashMap<>();

        for (Reserva r : reservas) {
            if (r.id() == null) {
                continue;
            }
            List<Periodo> periodosVisiveis = new ArrayList<>();
            for (Periodo periodo : r.periodos()) {
                if (porData.containsKey(periodo.inicio().toLocalDate())) {
                    periodosVisiveis.add(periodo);
                }
            }
            if (periodosVisiveis.isEmpty()) {
                continue;
            }
            // Dados comuns da Reserva, carregados só se houver Período visível
            String ambiente = descricaoAmbiente(r, ambientes);
            List<RecursoCard> listaRecursos = recursosDoCard(r, recursos);
            List<PedidoSnpCard> pedidos = List.copyOf(fonte.pedidosSnp(r.id()));
            for (Periodo periodo : periodosVisiveis) {
                Card card = new Card(Long.toString(r.id()), DATA_HORA.format(periodo.inicio()),
                        DATA_HORA.format(periodo.termino()), r.finalidade(),
                        // Sem nome persistido: usa o sub do Cognito (ver Javadoc da classe)
                        r.solicitante(), ambiente, listaRecursos, pedidos, r.cancelada());
                porData.get(periodo.inicio().toLocalDate()).add(new CardOrdenado(periodo.inicio(), r.id(), card));
            }
        }

        Comparator<CardOrdenado> ordem = Comparator.comparing(CardOrdenado::inicio)
                .thenComparingLong(CardOrdenado::reservaId);
        List<Coluna> colunas = new ArrayList<>(porData.size());
        porData.forEach((data, cards) -> colunas.add(new Coluna(data.toString(),
                cards.stream().sorted(ordem).map(CardOrdenado::card).toList())));
        return new RespostaPainel(colunas);
    }

    /** Descrição do Ambiente; para Local_Proprio usa o complemento informado. */
    private String descricaoAmbiente(Reserva r, Map<Long, String> cache) {
        if (r.localProprio()) {
            String complemento = aparar(r.complemento());
            return complemento == null ? "Local próprio" : "Local próprio: " + complemento;
        }
        return cache.computeIfAbsent(r.ambienteId(),
                id -> fonte.descricaoAmbiente(id).orElse("Ambiente " + id));
    }

    private List<RecursoCard> recursosDoCard(Reserva r, Map<Long, String> cache) {
        List<RecursoCard> lista = new ArrayList<>(r.solicitacoes().size());
        for (Solicitacao s : r.solicitacoes()) {
            String descricao = cache.computeIfAbsent(s.recursoId(),
                    id -> fonte.descricaoRecurso(id).orElse("Recurso " + id));
            lista.add(new RecursoCard(descricao, s.quantidade() > 0 ? s.quantidade() : null));
        }
        return List.copyOf(lista);
    }

    /** Datas das colunas, pulando sábados e domingos quando {@code fds} for falso. */
    static List<LocalDate> datas(Consulta c) {
        List<LocalDate> datas = new ArrayList<>(c.colunas());
        LocalDate data = c.data();
        while (datas.size() < c.colunas()) {
            DayOfWeek dia = data.getDayOfWeek();
            if (c.fds() || (dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY)) {
                datas.add(data);
            }
            data = data.plusDays(1);
        }
        return datas;
    }

    private static String aparar(String valor) {
        if (valor == null) {
            return null;
        }
        String s = valor.trim();
        return s.isEmpty() ? null : s;
    }

    /** Card com a chave de ordenação (início, depois id da Reserva para desempate estável). */
    private record CardOrdenado(LocalDateTime inicio, long reservaId, Card card) {
    }
}
