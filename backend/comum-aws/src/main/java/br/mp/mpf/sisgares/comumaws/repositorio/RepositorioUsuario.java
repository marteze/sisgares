package br.mp.mpf.sisgares.comumaws.repositorio;

import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.lerNumero;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.s;
import static br.mp.mpf.sisgares.comumaws.repositorio.Atributos.texto;

import br.mp.mpf.sisgares.comumaws.dynamo.Chaves;
import br.mp.mpf.sisgares.comumaws.dynamo.NomesTabela;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/** Repositório de Usuário: GetItem/PutItem em {@code USER#<sub>}/META (Req. 3.5). */
public final class RepositorioUsuario {

    static final String SUB = "sub";
    static final String PERFIL = "perfil";
    static final String UNIDADE = NomesTabela.UNIDADE;
    static final String ENVO_ID = "envoId";

    private final OperacoesDynamo dynamo;

    public RepositorioUsuario(DynamoDbClient cliente, String nomeTabela) {
        this.dynamo = new OperacoesDynamo(cliente, nomeTabela);
    }

    public Optional<Usuario> obter(String sub) {
        return dynamo.obter(Chaves.pkUsuario(sub), Chaves.META).map(RepositorioUsuario::deItem);
    }

    public void salvar(Usuario usuario) {
        dynamo.gravar(paraItem(usuario));
    }

    static Map<String, AttributeValue> paraItem(Usuario u) {
        Map<String, AttributeValue> item = Atributos.chave(Chaves.pkUsuario(u.sub()), Chaves.META);
        item.put(SUB, s(u.sub()));
        item.put(PERFIL, s(u.perfil().name()));
        item.put(UNIDADE, s(u.unidade()));
        Atributos.numeroOpcional(item, ENVO_ID, u.envoId());
        return item;
    }

    static Usuario deItem(Map<String, AttributeValue> item) {
        return new Usuario(texto(item, SUB), Perfil.valueOf(texto(item, PERFIL)),
                texto(item, UNIDADE), lerNumero(item, ENVO_ID));
    }
}
