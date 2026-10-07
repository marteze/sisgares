package br.mp.mpf.sisgares.comumaws.auth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.verifiedpermissions.VerifiedPermissionsClient;
import software.amazon.awssdk.services.verifiedpermissions.model.ActionIdentifier;
import software.amazon.awssdk.services.verifiedpermissions.model.AttributeValue;
import software.amazon.awssdk.services.verifiedpermissions.model.Decision;
import software.amazon.awssdk.services.verifiedpermissions.model.EntitiesDefinition;
import software.amazon.awssdk.services.verifiedpermissions.model.EntityIdentifier;
import software.amazon.awssdk.services.verifiedpermissions.model.EntityItem;
import software.amazon.awssdk.services.verifiedpermissions.model.IsAuthorizedRequest;
import software.amazon.awssdk.services.verifiedpermissions.model.IsAuthorizedResponse;

/**
 * Autorizador que consulta o Amazon Verified Permissions ({@code IsAuthorized}) com as políticas
 * Cedar do namespace {@code Sisgares} (Req. 5.5, 5.6).
 *
 * <p>Monta as entidades {@code Sisgares::Usuario} (grupos, unidade, setor) e
 * {@code Sisgares::Reserva} (dono, unidade, setores). Qualquer resposta diferente de ALLOW é negação.
 * Nada do principal é registrado em log aqui (sem dados pessoais).
 */
public final class AutorizadorVerifiedPermissions implements Autorizador {

    static final String TIPO_USUARIO = "Sisgares::Usuario";
    static final String TIPO_RESERVA = "Sisgares::Reserva";
    static final String TIPO_ACAO = "Sisgares::Action";
    /** Id do dono quando o recurso não tem Solicitante (ex.: painel); não corresponde a nenhum sub. */
    static final String SEM_DONO = "#sem-dono";

    private final VerifiedPermissionsClient cliente;
    private final String policyStoreId;

    public AutorizadorVerifiedPermissions(VerifiedPermissionsClient cliente, String policyStoreId) {
        this.cliente = Objects.requireNonNull(cliente, "cliente");
        this.policyStoreId = Objects.requireNonNull(policyStoreId, "policyStoreId");
    }

    /** Cliente único por contêiner (região e credenciais pela cadeia padrão do SDK). */
    static AutorizadorVerifiedPermissions padrao(String policyStoreId) {
        return new AutorizadorVerifiedPermissions(ClientePadrao.INSTANCIA, policyStoreId);
    }

    @Override
    public boolean permitido(Principal principal, Acao acao, RecursoAutorizacao recurso) {
        IsAuthorizedResponse resposta = cliente.isAuthorized(requisicao(principal, acao, recurso));
        return resposta.decision() == Decision.ALLOW;
    }

    /** Monta a requisição {@code IsAuthorized} com a lista de entidades (visível para testes). */
    IsAuthorizedRequest requisicao(Principal principal, Acao acao, RecursoAutorizacao recurso) {
        Objects.requireNonNull(principal, "principal é obrigatório");
        Objects.requireNonNull(acao, "acao é obrigatória");
        Objects.requireNonNull(recurso, "recurso é obrigatório");
        EntityIdentifier idUsuario = usuario(principal.sub());
        EntityIdentifier idReserva = EntityIdentifier.builder().entityType(TIPO_RESERVA).entityId(recurso.id()).build();
        return IsAuthorizedRequest.builder()
                .policyStoreId(policyStoreId)
                .principal(idUsuario)
                .action(ActionIdentifier.builder().actionType(TIPO_ACAO).actionId(acao.idCedar()).build())
                .resource(idReserva)
                .entities(EntitiesDefinition.builder().entityList(List.of(
                        entidadeUsuario(idUsuario, principal),
                        entidadeReserva(idReserva, recurso))).build())
                .build();
    }

    private static EntityItem entidadeUsuario(EntityIdentifier id, Principal p) {
        List<AttributeValue> grupos = new ArrayList<>();
        p.grupos().forEach(g -> grupos.add(texto(g)));
        Map<String, AttributeValue> atributos = new HashMap<>();
        atributos.put("grupos", AttributeValue.builder().set(grupos).build());
        // unidade é obrigatória no schema; ausente vira ""
        atributos.put("unidade", texto(p.unidade() == null ? "" : p.unidade()));
        if (p.setor() != null && !p.setor().isBlank()) {
            atributos.put("setor", texto(p.setor().trim()));
        }
        return EntityItem.builder().identifier(id).attributes(atributos).build();
    }

    private static EntityItem entidadeReserva(EntityIdentifier id, RecursoAutorizacao r) {
        List<AttributeValue> setores = new ArrayList<>();
        r.setores().stream().sorted().forEach(s -> setores.add(texto(s)));
        Map<String, AttributeValue> atributos = Map.of(
                "dono", AttributeValue.builder().entityIdentifier(usuario(r.dono() == null ? SEM_DONO : r.dono())).build(),
                "unidade", texto(r.unidade()),
                "setores", AttributeValue.builder().set(setores).build());
        return EntityItem.builder().identifier(id).attributes(atributos).build();
    }

    private static EntityIdentifier usuario(String sub) {
        return EntityIdentifier.builder().entityType(TIPO_USUARIO).entityId(sub).build();
    }

    private static AttributeValue texto(String valor) {
        return AttributeValue.builder().string(valor).build();
    }

    /** Holder preguiçoso: o cliente só é criado quando o AVP é usado. */
    private static final class ClientePadrao {
        static final VerifiedPermissionsClient INSTANCIA = VerifiedPermissionsClient.create();
    }
}
