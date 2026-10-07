package br.mp.mpf.sisgares.comumaws.repositorio;

import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.indicador;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.lerNumero;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.lerTexto;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.listaTexto;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.listaTextoOpcional;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.n;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.numero;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.numeroOpcional;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.s;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.st;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.texto;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.textoOpcional;

import br.mp.mpf.sisgares.comumaws.dynamo.Chaves;
import br.mp.mpf.sisgares.comumaws.dynamo.NomesTabela;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Disposicao;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.GrupoRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroAmbiente;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Setor;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.UnidadeMacro;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoRecursoAmbiente;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoSetor;
import br.mp.mpf.sisgares.dominio.Ambiente;
import br.mp.mpf.sisgares.dominio.Recurso;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Mapeadores manuais entre registros do catálogo e itens DynamoDB.
 *
 * <p>Os métodos {@code paraItem*} geram o item principal com PK/SK; os métodos {@code de*}
 * leem apenas atributos de negócio, por isso servem tanto para o item principal quanto para
 * a cópia no item índice {@code CATALOGO#<tipo>}.
 */
final class MapeadorCatalogo {

    // Atributos auxiliares (fora dos CSVs)
    static final String RAIZ_ID = NomesTabela.RAIZ_ID;
    static final String UNIDADE = NomesTabela.UNIDADE;
    static final String NOME = "nome";
    static final String CODIGO = "codigo";
    static final String COD_SERVICO_SNP = "codServicoSnp";
    static final String EMAILS_ALTERNATIVOS = "emailsAlternativos";
    static final String ALT = "alt";
    static final String AMBIENTES_VINCULADOS = "ambientesVinculados";

    /** Atributos opcionais do Recurso removidos no Update quando ausentes. */
    static final List<String> OPCIONAIS_RECURSO =
            List.of(UNIDADE, "RECU_GREC_ID", "RECU_ICONE_ARQUIVO", "RECU_ICONE_REF_ORIGINAL");

    private MapeadorCatalogo() {
    }

    private static Map<String, AttributeValue> base(String pk, String sk) {
        return Atributos.chave(pk, sk);
    }

    // ---------- Unidade_Macro ----------

    static Map<String, AttributeValue> paraItem(UnidadeMacro u) {
        Map<String, AttributeValue> item = base(Chaves.pkUnidade(u.codigo()), Chaves.META);
        item.put(CODIGO, s(u.codigo()));
        item.put(NOME, s(u.nome()));
        return item;
    }

    static UnidadeMacro deUnidade(Map<String, AttributeValue> item) {
        return new UnidadeMacro(texto(item, CODIGO), texto(item, NOME));
    }

    // ---------- Ambiente ----------

    static Map<String, AttributeValue> paraItem(RegistroAmbiente r) {
        Ambiente a = r.ambiente();
        Map<String, AttributeValue> item = base(Chaves.pkAmbiente(a.id()), Chaves.META);
        item.put("AMBI_ID", n(a.id()));
        item.put("AMBI_DESC", s(a.descricao()));
        item.put("AMBI_ST_ATIVO", st(a.ativo()));
        numeroOpcional(item, "AMBI_ID_PAI", a.idPai());
        item.put(UNIDADE, s(a.unidade()));
        item.put(RAIZ_ID, n(r.raizId()));
        return item;
    }

    static RegistroAmbiente deAmbiente(Map<String, AttributeValue> item) {
        Ambiente a = new Ambiente(numero(item, "AMBI_ID"), texto(item, "AMBI_DESC"),
                indicador(item, "AMBI_ST_ATIVO"), lerNumero(item, "AMBI_ID_PAI"), texto(item, UNIDADE));
        return new RegistroAmbiente(a, numero(item, RAIZ_ID));
    }

    // ---------- Recurso ----------

    /** Item do Recurso sem o conjunto de ambientes vinculados (mantido pelas operações de VREC). */
    static Map<String, AttributeValue> paraItem(RegistroRecurso r) {
        Recurso rec = r.recurso();
        Map<String, AttributeValue> item = base(Chaves.pkRecurso(rec.id()), Chaves.META);
        item.put("RECU_ID", n(rec.id()));
        item.put("RECU_DESC", s(rec.descricao()));
        numeroOpcional(item, "RECU_GREC_ID", r.grupoId());
        item.put("RECU_ST_LIMITADO", st(rec.limitado()));
        item.put("RECU_DISPONIBILIDADE", n(rec.disponibilidade()));
        item.put("RECU_ST_ATIVO", st(rec.ativo()));
        textoOpcional(item, "RECU_ICONE_ARQUIVO", r.iconeArquivo());
        textoOpcional(item, "RECU_ICONE_REF_ORIGINAL", r.iconeRefOriginal());
        textoOpcional(item, UNIDADE, rec.unidade());
        return item;
    }

    static RegistroRecurso deRecurso(Map<String, AttributeValue> item) {
        AttributeValue vinculados = item.get(AMBIENTES_VINCULADOS);
        Set<Long> ambientes = vinculados != null && vinculados.hasNs()
                ? vinculados.ns().stream().map(Long::valueOf).collect(Collectors.toSet())
                : Set.of();
        Recurso rec = new Recurso(numero(item, "RECU_ID"), texto(item, "RECU_DESC"),
                indicador(item, "RECU_ST_ATIVO"), indicador(item, "RECU_ST_LIMITADO"),
                (int) numero(item, "RECU_DISPONIBILIDADE"), lerTexto(item, UNIDADE), ambientes);
        return new RegistroRecurso(rec, lerNumero(item, "RECU_GREC_ID"),
                lerTexto(item, "RECU_ICONE_ARQUIVO"), lerTexto(item, "RECU_ICONE_REF_ORIGINAL"));
    }

    // ---------- Disposição ----------

    static Map<String, AttributeValue> paraItem(Disposicao d) {
        Map<String, AttributeValue> item = base(Chaves.pkDisposicao(d.id()), Chaves.META);
        item.put("DISP_ID", n(d.id()));
        item.put("DISP_DESC", s(d.descricao()));
        item.put("DISP_ST_ATIVO", st(d.ativo()));
        textoOpcional(item, "DISP_ICONE_ARQUIVO", d.iconeArquivo());
        textoOpcional(item, "DISP_ICONE_REF_ORIGINAL", d.iconeRefOriginal());
        textoOpcional(item, ALT, d.textoAlternativo());
        return item;
    }

    static Disposicao deDisposicao(Map<String, AttributeValue> item) {
        return new Disposicao(numero(item, "DISP_ID"), texto(item, "DISP_DESC"), indicador(item, "DISP_ST_ATIVO"),
                lerTexto(item, "DISP_ICONE_ARQUIVO"), lerTexto(item, "DISP_ICONE_REF_ORIGINAL"), lerTexto(item, ALT));
    }

    // ---------- Grupo ----------

    static Map<String, AttributeValue> paraItem(GrupoRecurso g) {
        Map<String, AttributeValue> item = base(Chaves.pkGrupo(g.id()), Chaves.META);
        item.put("GREC_ID", n(g.id()));
        item.put("GREC_DESC", s(g.descricao()));
        item.put("GREC_ORDEM", n(g.ordem()));
        item.put("GREC_ST_ATIVO", st(g.ativo()));
        return item;
    }

    static GrupoRecurso deGrupo(Map<String, AttributeValue> item) {
        return new GrupoRecurso(numero(item, "GREC_ID"), texto(item, "GREC_DESC"),
                (int) numero(item, "GREC_ORDEM"), indicador(item, "GREC_ST_ATIVO"));
    }

    // ---------- Setor ----------

    static Map<String, AttributeValue> paraItem(Setor e) {
        Map<String, AttributeValue> item = base(Chaves.pkSetor(e.id()), Chaves.META);
        item.put("ENVO_ID", n(e.id()));
        item.put("ENVO_DESC", s(e.descricao()));
        textoOpcional(item, "ENVO_EMAIL", e.email());
        item.put("ENVO_ST_ATIVO", st(e.ativo()));
        item.put(UNIDADE, s(e.unidade()));
        listaTextoOpcional(item, EMAILS_ALTERNATIVOS, e.emailsAlternativos());
        return item;
    }

    static Setor deSetor(Map<String, AttributeValue> item) {
        return new Setor(numero(item, "ENVO_ID"), texto(item, "ENVO_DESC"), lerTexto(item, "ENVO_EMAIL"),
                indicador(item, "ENVO_ST_ATIVO"), texto(item, UNIDADE), listaTexto(item, EMAILS_ALTERNATIVOS));
    }

    // ---------- Vínculos ----------

    static Map<String, AttributeValue> paraItemEamb(VinculoSetor v) {
        Map<String, AttributeValue> item = base(Chaves.pkAmbiente(v.alvoId()), Chaves.skEamb(v.envoId()));
        item.put("EAMB_ID", n(v.id()));
        item.put("EAMB_ENVO_ID", n(v.envoId()));
        item.put("EAMB_AMBI_ID", n(v.alvoId()));
        textoOpcional(item, COD_SERVICO_SNP, v.codServicoSnp());
        return item;
    }

    static VinculoSetor deEamb(Map<String, AttributeValue> item) {
        return new VinculoSetor(numero(item, "EAMB_ID"), numero(item, "EAMB_ENVO_ID"),
                numero(item, "EAMB_AMBI_ID"), lerTexto(item, COD_SERVICO_SNP));
    }

    static Map<String, AttributeValue> paraItemErec(VinculoSetor v) {
        Map<String, AttributeValue> item = base(Chaves.pkRecurso(v.alvoId()), Chaves.skErec(v.envoId()));
        item.put("EREC_ID", n(v.id()));
        item.put("EREC_ENVO_ID", n(v.envoId()));
        item.put("EREC_RECU_ID", n(v.alvoId()));
        textoOpcional(item, COD_SERVICO_SNP, v.codServicoSnp());
        return item;
    }

    static VinculoSetor deErec(Map<String, AttributeValue> item) {
        return new VinculoSetor(numero(item, "EREC_ID"), numero(item, "EREC_ENVO_ID"),
                numero(item, "EREC_RECU_ID"), lerTexto(item, COD_SERVICO_SNP));
    }

    static Map<String, AttributeValue> paraItem(VinculoRecursoAmbiente v) {
        Map<String, AttributeValue> item = base(Chaves.pkAmbiente(v.ambienteId()), Chaves.skVrec(v.recursoId()));
        item.put("VREC_ID", n(v.id()));
        item.put("VREC_RECU_ID", n(v.recursoId()));
        item.put("VREC_AMBI_ID", n(v.ambienteId()));
        return item;
    }

    static VinculoRecursoAmbiente deVrec(Map<String, AttributeValue> item) {
        return new VinculoRecursoAmbiente(numero(item, "VREC_ID"), numero(item, "VREC_RECU_ID"),
                numero(item, "VREC_AMBI_ID"));
    }

    /** Cópia do item principal com a chave do item índice ({@code PK=CATALOGO#<tipo>}, {@code SK=<id>}). */
    static Map<String, AttributeValue> paraIndice(Map<String, AttributeValue> principal, String tipo, String id) {
        Map<String, AttributeValue> indice = new HashMap<>(principal);
        indice.putAll(Atributos.chave(RepositorioCatalogo.PREFIXO_CATALOGO + tipo, id));
        return indice;
    }
}
