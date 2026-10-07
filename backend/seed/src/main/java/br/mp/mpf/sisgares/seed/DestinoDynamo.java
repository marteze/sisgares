package br.mp.mpf.sisgares.seed;

import br.mp.mpf.sisgares.comumaws.dynamo.Chaves;
import br.mp.mpf.sisgares.comumaws.dynamo.NomesTabela;
import br.mp.mpf.sisgares.comumaws.repositorio.MapeadorReserva;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Disposicao;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.GrupoRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroAmbiente;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.RegistroRecurso;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Setor;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.UnidadeMacro;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoRecursoAmbiente;
import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.VinculoSetor;
import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioCatalogo;
import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioConfiguracao;
import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioUsuario;
import br.mp.mpf.sisgares.comumaws.repositorio.Usuario;
import br.mp.mpf.sisgares.dominio.Configuracao;
import br.mp.mpf.sisgares.dominio.Periodo;
import br.mp.mpf.sisgares.dominio.Reserva;
import br.mp.mpf.sisgares.dominio.Solicitacao;
import br.mp.mpf.sisgares.seed.ReservaSeed.SolicitacaoSeed;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

/**
 * {@link DestinoSeed} no DynamoDB. Catálogo, configuração e usuários usam os repositórios do
 * {@code comum-aws} (Put/Update por chave); a Reserva grava cada item (META, PRES, SOLI e cópias
 * de GSI) com PutItem nas mesmas chaves, o que torna a carga idempotente (Requisito 4.12).
 */
public final class DestinoDynamo implements DestinoSeed {

    /**
     * Item de texto alternativo da Imagem_Ícone: {@code PK=ICONE#<nome>}, {@code SK=META}.
     * Padrão de acesso novo: deve constar no modelo em {@code docs/}.
     */
    static final String PREFIXO_ICONE = "ICONE#";
    static final String SOLI_QTD = "SOLI_QTD";
    /** criadoEm fixo das reservas do seed: mantém GSI4SK idêntico entre execuções. */
    static final LocalDateTime CRIADO_EM_SEED = LocalDateTime.of(2026, 1, 1, 0, 0);

    private final DynamoDbClient cliente;
    private final String tabela;
    private final RepositorioCatalogo catalogo;
    private final RepositorioConfiguracao configuracao;
    private final RepositorioUsuario usuarios;

    public DestinoDynamo(DynamoDbClient cliente, String tabela, Clock clock) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.tabela = Objects.requireNonNull(tabela, "tabela");
        this.catalogo = new RepositorioCatalogo(cliente, tabela);
        this.configuracao = new RepositorioConfiguracao(cliente, tabela, clock);
        this.usuarios = new RepositorioUsuario(cliente, tabela);
    }

    @Override
    public void salvarUnidade(UnidadeMacro unidade) {
        catalogo.salvarUnidade(unidade);
    }

    @Override
    public void salvarConfiguracao(Configuracao cfg, String snpUrl) {
        configuracao.salvar(cfg, snpUrl);
    }

    @Override
    public void salvarUsuario(Usuario usuario) {
        usuarios.salvar(usuario);
    }

    @Override
    public void salvarGrupo(GrupoRecurso grupo) {
        catalogo.salvarGrupo(grupo);
    }

    @Override
    public void salvarDisposicao(Disposicao disposicao) {
        catalogo.salvarDisposicao(disposicao);
    }

    @Override
    public void salvarAmbiente(RegistroAmbiente ambiente) {
        catalogo.salvarAmbiente(ambiente);
    }

    @Override
    public void salvarSetor(Setor setor) {
        catalogo.salvarSetor(setor);
    }

    @Override
    public void salvarRecurso(RegistroRecurso recurso) {
        catalogo.salvarRecurso(recurso);
    }

    @Override
    public void vincularSetorAmbiente(VinculoSetor eamb) {
        catalogo.vincularSetorAmbiente(eamb);
    }

    @Override
    public void vincularSetorRecurso(VinculoSetor erec) {
        catalogo.vincularSetorRecurso(erec);
    }

    @Override
    public void vincularRecursoAmbiente(VinculoRecursoAmbiente vrec) {
        // ADD no conjunto ambientesVinculados: repetir o vínculo não duplica
        catalogo.vincularRecursoAmbiente(vrec);
    }

    @Override
    public void salvarTextoAlternativo(String nomeImagem, String categoria, String alt) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(NomesTabela.PK, texto(PREFIXO_ICONE + nomeImagem));
        item.put(NomesTabela.SK, texto(Chaves.META));
        item.put("nome", texto(nomeImagem));
        item.put("categoria", texto(categoria));
        item.put("alt", texto(alt));
        gravar(item);
    }

    @Override
    public void salvarReserva(ReservaSeed seed) {
        Reserva r = seed.reserva();
        List<Map<String, AttributeValue>> itens = new ArrayList<>();
        itens.add(MapeadorReserva.paraItemMeta(r, CRIADO_EM_SEED));
        for (Periodo p : r.periodos()) {
            itens.addAll(MapeadorReserva.itensPeriodo(r, p, seed.raizId(), seed.setores()));
        }
        // Preserva o SOLI_ID original (itensFilhos usaria o RECU_ID como SOLI_ID)
        for (SolicitacaoSeed s : seed.solicitacoes()) {
            Solicitacao sol = new Solicitacao(s.recursoId(), s.quantidade() == null ? 0 : s.quantidade());
            for (Map<String, AttributeValue> item : MapeadorReserva.itensSolicitacao(r, s.soliId(), sol)) {
                Map<String, AttributeValue> copia = new HashMap<>(item);
                if (s.quantidade() == null) {
                    // Recurso não limitado com SOLI_QTD vazio: quantidade ausente (Req. 4.5)
                    copia.remove(SOLI_QTD);
                }
                itens.add(copia);
            }
        }
        for (Map<String, AttributeValue> item : itens) {
            gravar(item);
        }
    }

    private void gravar(Map<String, AttributeValue> item) {
        cliente.putItem(PutItemRequest.builder().tableName(tabela).item(item).build());
    }

    private static AttributeValue texto(String valor) {
        return AttributeValue.builder().s(valor).build();
    }
}
