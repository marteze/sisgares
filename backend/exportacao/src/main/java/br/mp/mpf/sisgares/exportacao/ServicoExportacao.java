package br.mp.mpf.sisgares.exportacao;

import br.mp.mpf.sisgares.comumaws.auth.Acao;
import br.mp.mpf.sisgares.comumaws.auth.Autorizador;
import br.mp.mpf.sisgares.comumaws.auth.Principal;
import br.mp.mpf.sisgares.comumaws.auth.RecursoAutorizacao;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoAcessoNegado;
import br.mp.mpf.sisgares.comumaws.http.ExcecaoValidacao;
import br.mp.mpf.sisgares.dominio.ParametrosGrade;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.Solicitacao;
import br.mp.mpf.sisgares.dominio.Violacao;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
 * Monta as linhas da exportação (Req. 17.1, 17.2) com as mesmas regras de visibilidade do
 * Painel do Atendente (Req. 16): Administrador vê a Unidade_Macro (GSI3), Setor_Atendente vê o seu
 * Setor_Envolvido (GSI2) e os demais perfis recebem 403.
 *
 * <p>Cópia mínima da lógica de {@code paineis.ServicoPainelAtendente}: o contexto exportacao não pode
 * depender de paineis (dependências seguem contextos → comum-aws → dominio).
 */
public final class ServicoExportacao {

    public static final String GRUPO_ATENDENTE = "Setor_Atendente";
    public static final String GRUPO_ADMINISTRADOR = "Administrador";
    public static final String PARAMETRO_INVALIDO = "PARAMETRO_INVALIDO";

    /** Formatos aceitos. */
    public enum Formato {
        CSV("csv", "text/csv; charset=utf-8"),
        PDF("pdf", "application/pdf");

        private final String extensao;
        private final String contentType;

        Formato(String extensao, String contentType) {
            this.extensao = extensao;
            this.contentType = contentType;
        }

        public String extensao() {
            return extensao;
        }

        public String contentType() {
            return contentType;
        }
    }

    /** Fonte de dados (o Handler a implementa com os repositórios de comum-aws). */
    public interface FonteDados {
        List<Reserva> porSetor(long envoId, LocalDateTime de, LocalDateTime ate);

        List<Reserva> porUnidade(String unidade, LocalDateTime de, LocalDateTime ate);

        Optional<String> descricaoAmbiente(long ambienteId);

        Optional<String> descricaoRecurso(long recursoId);
    }

    /** Parâmetros já validados. */
    public record Consulta(Formato formato, LocalDate data, int colunas, boolean fds) {
    }

    /** Uma linha por Período exportado. */
    public record Linha(long reservaId, LocalDateTime inicio, LocalDateTime termino, String ambiente,
                        String finalidade, String solicitante, String recursos, boolean cancelada) {
    }

    /** Linhas de uma data, ordenadas por início (Req. 17.2). */
    public record Grupo(LocalDate data, List<Linha> linhas) {
    }

    private final FonteDados fonte;
    private final Autorizador autorizador;

    public ServicoExportacao(FonteDados fonte, Autorizador autorizador) {
        this.fonte = Objects.requireNonNull(fonte, "fonte é obrigatória");
        this.autorizador = Objects.requireNonNull(autorizador, "autorizador é obrigatório");
    }

    /**
     * Valida o corpo {@code {formato, data, colunas, fds}}, acumulando todas as violações (HTTP 422).
     * {@code colunas} (padrão 7, de 1 a 14) e {@code fds} (padrão false) são opcionais.
     */
    public static Consulta lerParametros(Map<String, ?> corpo) {
        Map<String, ?> p = corpo == null ? Map.of() : corpo;
        List<Violacao> violacoes = new ArrayList<>();

        Formato formato = null;
        String textoFormato = texto(p.get("formato"));
        if ("csv".equalsIgnoreCase(textoFormato)) {
            formato = Formato.CSV;
        } else if ("pdf".equalsIgnoreCase(textoFormato)) {
            formato = Formato.PDF;
        } else {
            violacoes.add(new Violacao(PARAMETRO_INVALIDO, "Informe o formato como csv ou pdf.", "formato"));
        }

        LocalDate data;
        try {
            String textoData = texto(p.get("data"));
            data = textoData == null ? null : LocalDate.parse(textoData);
        } catch (DateTimeParseException e) {
            data = null;
        }
        if (data == null) {
            violacoes.add(new Violacao(PARAMETRO_INVALIDO,
                    "Informe a data de referência no formato AAAA-MM-DD.", "data"));
        }

        int colunas = ParametrosGrade.COLUNAS_PADRAO;
        String textoColunas = texto(p.get("colunas"));
        if (textoColunas != null) {
            try {
                colunas = Integer.parseInt(textoColunas);
            } catch (NumberFormatException e) {
                colunas = -1;
            }
            if (colunas < ParametrosGrade.COLUNAS_MINIMO || colunas > ParametrosGrade.COLUNAS_MAXIMO) {
                violacoes.add(new Violacao(PARAMETRO_INVALIDO, "Informe o número de colunas entre "
                        + ParametrosGrade.COLUNAS_MINIMO + " e " + ParametrosGrade.COLUNAS_MAXIMO + ".", "colunas"));
            }
        }

        boolean fds = false;
        String textoFds = texto(p.get("fds"));
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
        return new Consulta(formato, data, colunas, fds);
    }

    /**
     * Linhas agrupadas por data exibida, conforme o perfil do usuário.
     *
     * @throws ExcecaoAcessoNegado (403) para perfis sem acesso ao Painel do Atendente
     */
    public List<Grupo> consultar(Consulta c, Principal usuario) {
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
            autorizador.exigir(usuario, Acao.VER_PAINEL_ATENDENTE, RecursoAutorizacao.painelDe(usuario));
            reservas = fonte.porUnidade(unidade, de, ate);
        } else if (usuario.pertenceA(GRUPO_ATENDENTE)) {
            long setor = setor(usuario);
            autorizador.exigir(usuario, Acao.VER_PAINEL_ATENDENTE, RecursoAutorizacao.painelDe(usuario));
            reservas = fonte.porSetor(setor, de, ate);
        } else {
            throw new ExcecaoAcessoNegado("Perfil sem acesso à exportação do Painel do Atendente.");
        }
        return montar(datas, reservas);
    }

    private List<Grupo> montar(List<LocalDate> datas, List<Reserva> reservas) {
        Map<LocalDate, List<Linha>> porData = new LinkedHashMap<>();
        datas.forEach(d -> porData.put(d, new ArrayList<>()));
        Map<Long, String> ambientes = new HashMap<>();
        Map<Long, String> recursos = new HashMap<>();

        for (Reserva r : reservas) {
            if (r.id() == null) {
                continue;
            }
            String ambiente = null;
            String listaRecursos = null;
            for (Periodo periodo : r.periodos()) {
                List<Linha> destino = porData.get(periodo.inicio().toLocalDate());
                if (destino == null) {
                    continue; // fora das colunas exibidas (ex.: fim de semana oculto)
                }
                if (ambiente == null) {
                    ambiente = descricaoAmbiente(r, ambientes);
                    listaRecursos = recursos(r, recursos);
                }
                // Sem nome persistido: usa o sub do Cognito, como no painel
                destino.add(new Linha(r.id(), periodo.inicio(), periodo.termino(), ambiente,
                        r.finalidade(), r.solicitante(), listaRecursos, r.cancelada()));
            }
        }

        Comparator<Linha> ordem = Comparator.comparing(Linha::inicio).thenComparingLong(Linha::reservaId);
        List<Grupo> grupos = new ArrayList<>(porData.size());
        porData.forEach((data, linhas) -> grupos.add(new Grupo(data, linhas.stream().sorted(ordem).toList())));
        return grupos;
    }

    private String descricaoAmbiente(Reserva r, Map<Long, String> cache) {
        if (r.localProprio()) {
            String complemento = aparar(r.complemento());
            return complemento == null ? "Local próprio" : "Local próprio: " + complemento;
        }
        return cache.computeIfAbsent(r.ambienteId(),
                id -> fonte.descricaoAmbiente(id).orElse("Ambiente " + id));
    }

    /** Recursos em texto, ex.: {@code "Projetor (2), Notebook"}. */
    private String recursos(Reserva r, Map<Long, String> cache) {
        List<String> partes = new ArrayList<>(r.solicitacoes().size());
        for (Solicitacao s : r.solicitacoes()) {
            String descricao = cache.computeIfAbsent(s.recursoId(),
                    id -> fonte.descricaoRecurso(id).orElse("Recurso " + id));
            partes.add(s.quantidade() > 0 ? descricao + " (" + s.quantidade() + ")" : descricao);
        }
        return String.join(", ", partes);
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

    /** Converte valores JSON (texto, número ou booleano) em texto aparado. */
    private static String texto(Object valor) {
        return valor == null ? null : aparar(valor.toString());
    }

    private static String aparar(String valor) {
        if (valor == null) {
            return null;
        }
        String s = valor.trim();
        return s.isEmpty() ? null : s;
    }
}
