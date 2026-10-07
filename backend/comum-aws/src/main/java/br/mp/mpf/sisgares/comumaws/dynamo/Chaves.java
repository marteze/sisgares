package br.mp.mpf.sisgares.comumaws.dynamo;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Montagem das chaves PK/SK e das chaves dos GSIs conforme o design (seção Data Models).
 *
 * <p>Datas nas SKs usam formato ISO-8601 de largura fixa ({@code yyyy-MM-dd'T'HH:mm:ss})
 * para que a ordem lexicográfica coincida com a cronológica nas Queries com BETWEEN.
 */
public final class Chaves {

    public static final String SEPARADOR = "#";
    public static final String META = "META";
    public static final String CTRL = "CTRL";
    public static final String GLOBAL = "GLOBAL";
    public static final String CONFIG = "CONFIG";
    public static final String OUTBOX = "OUTBOX";

    // Prefixos
    public static final String UNID = "UNID#";
    public static final String AMBI = "AMBI#";
    public static final String EAMB = "EAMB#";
    public static final String VREC = "VREC#";
    public static final String DISP = "DISP#";
    public static final String GREC = "GREC#";
    public static final String RECU = "RECU#";
    public static final String EREC = "EREC#";
    public static final String ENVO = "ENVO#";
    public static final String RESE = "RESE#";
    public static final String PRES = "PRES#";
    public static final String SOLI = "SOLI#";
    public static final String VERS = "VERS#";
    public static final String SNP = "SNP#";
    public static final String NOTI = "NOTI#";
    public static final String CTRL_AMBI = "CTRL#AMBI#";
    public static final String CTRL_RECU = "CTRL#RECU#";
    public static final String EVT = "EVT#";
    public static final String USER = "USER#";
    public static final String RAIZ = "RAIZ#";
    public static final String SOLIC = "SOLIC#";

    /** Formato de largura fixa (sem fração de segundo) usado nas SKs de data. */
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private Chaves() {
    }

    // ---------- Catálogo ----------

    public static String pkUnidade(String unidade) {
        return UNID + texto(unidade);
    }

    public static String pkAmbiente(Object ambienteId) {
        return AMBI + texto(ambienteId);
    }

    public static String skEamb(Object envoId) {
        return EAMB + texto(envoId);
    }

    public static String skVrec(Object recursoId) {
        return VREC + texto(recursoId);
    }

    public static String pkDisposicao(Object disposicaoId) {
        return DISP + texto(disposicaoId);
    }

    public static String pkGrupo(Object grupoId) {
        return GREC + texto(grupoId);
    }

    public static String pkRecurso(Object recursoId) {
        return RECU + texto(recursoId);
    }

    public static String skErec(Object envoId) {
        return EREC + texto(envoId);
    }

    public static String pkSetor(Object envoId) {
        return ENVO + texto(envoId);
    }

    public static String pkUsuario(String sub) {
        return USER + texto(sub);
    }

    // ---------- Reserva e itens filhos ----------

    public static String pkReserva(Object reservaId) {
        return RESE + texto(reservaId);
    }

    public static String skPeriodo(Object periodoId) {
        return PRES + texto(periodoId);
    }

    public static String skSolicitacao(Object solicitacaoId) {
        return SOLI + texto(solicitacaoId);
    }

    /** SK da versão com zeros à esquerda ({@code VERS#0001}) para manter a ordem. */
    public static String skVersao(int numero) {
        if (numero < 0) {
            throw new IllegalArgumentException("numero de versão negativo");
        }
        return VERS + String.format("%04d", numero);
    }

    public static String skPedidoSnp(Object vinculo) {
        return SNP + texto(vinculo);
    }

    public static String skNotificacao(Object eventoId, Object envoId) {
        return NOTI + texto(eventoId) + SEPARADOR + texto(envoId);
    }

    // ---------- Controle, eventos, outbox, configuração ----------

    /** PK do Item_Controle de uma árvore de ambientes ({@code CTRL#AMBI#<raizId>}); SK = {@link #CTRL}. */
    public static String pkControleAmbiente(Object raizId) {
        return CTRL_AMBI + texto(raizId);
    }

    /** PK do Item_Controle de um recurso limitado ({@code CTRL#RECU#<id>}); SK = {@link #CTRL}. */
    public static String pkControleRecurso(Object recursoId) {
        return CTRL_RECU + texto(recursoId);
    }

    /** PK do evento processado (idempotência); SK = nome do consumidor. */
    public static String pkEventoProcessado(String eventoId) {
        return EVT + texto(eventoId);
    }

    /** SK do outbox: {@code <ts ISO>#<eventoId>}, PK = {@link #OUTBOX}. */
    public static String skOutbox(Instant ts, String eventoId) {
        return Objects.requireNonNull(ts, "ts").toString() + SEPARADOR + texto(eventoId);
    }

    /** SK da configuração por unidade; a global usa {@link #GLOBAL}. PK = {@link #CONFIG}. */
    public static String skConfiguracaoUnidade(String unidade) {
        return UNID + texto(unidade);
    }

    // ---------- GSIs ----------

    public static String gsi1Pk(Object raizId) {
        return RAIZ + texto(raizId);
    }

    public static String gsi2Pk(Object envoId) {
        return ENVO + texto(envoId);
    }

    public static String gsi3Pk(String unidade) {
        return UNID + texto(unidade);
    }

    public static String gsi4Pk(String sub) {
        return SOLIC + texto(sub);
    }

    public static String gsi5Pk(Object recursoId) {
        return RECU + texto(recursoId);
    }

    /** SK comum a GSI1, GSI2, GSI3 e GSI5: {@code <dataIni ISO>#<reseId>}. */
    public static String skDataReserva(LocalDateTime dataIni, Object reservaId) {
        return data(dataIni) + SEPARADOR + texto(reservaId);
    }

    /** SK do GSI4: {@code <criadoEm ISO>#<reseId>}. */
    public static String gsi4Sk(LocalDateTime criadoEm, Object reservaId) {
        return skDataReserva(criadoEm, reservaId);
    }

    /**
     * Limite de janela para Query com BETWEEN (somente a data, sem o id).
     * O limite superior deve receber o sufixo {@link #limiteSuperior(String)} para incluir todos os ids.
     */
    public static String data(LocalDateTime dataHora) {
        return FORMATO_DATA.format(Objects.requireNonNull(dataHora, "dataHora"));
    }

    /** Acrescenta ao limite superior um sufixo maior que qualquer {@code #<id>}. */
    public static String limiteSuperior(String limite) {
        return limite + SEPARADOR + '\uffff';
    }

    private static String texto(Object valor) {
        String s = String.valueOf(Objects.requireNonNull(valor, "identificador"));
        if (s.isBlank()) {
            throw new IllegalArgumentException("identificador vazio");
        }
        return s;
    }
}
