package br.mp.mpf.sisgares.comumaws.repositorio;

import br.mp.mpf.sisgares.dominio.Ambiente;
import br.mp.mpf.sisgares.dominio.Recurso;
import java.util.List;
import java.util.Objects;

/**
 * Registros persistidos do catálogo que não têm (ou estendem) um record do {@code dominio}.
 * Nomes de atributos seguem os CSVs (Req. 3.1–3.3).
 */
public final class ModelosCatalogo {

    private ModelosCatalogo() {
    }

    /** Unidade_Macro ({@code UNID#<codigo>}/META). A faixa própria fica na Configuração ({@code CONFIG/UNID#<u>}). */
    public record UnidadeMacro(String codigo, String nome) {
        public UnidadeMacro {
            Objects.requireNonNull(codigo, "codigo é obrigatório");
            Objects.requireNonNull(nome, "nome é obrigatório");
        }
    }

    /** Ambiente com o id da raiz da sua árvore (usado no GSI1 e no Item_Controle). */
    public record RegistroAmbiente(Ambiente ambiente, long raizId) {
        public RegistroAmbiente {
            Objects.requireNonNull(ambiente, "ambiente é obrigatório");
        }
    }

    /** Recurso com os atributos de CSV não presentes no record do domínio. */
    public record RegistroRecurso(Recurso recurso, Long grupoId, String iconeArquivo, String iconeRefOriginal) {
        public RegistroRecurso {
            Objects.requireNonNull(recurso, "recurso é obrigatório");
        }
    }

    /** Disposição (DISP); {@code textoAlternativo} é o "alt" do ícone. */
    public record Disposicao(long id, String descricao, boolean ativo, String iconeArquivo,
                             String iconeRefOriginal, String textoAlternativo) {
        public Disposicao {
            Objects.requireNonNull(descricao, "descricao é obrigatória");
        }
    }

    /** Grupo de recursos (GREC). */
    public record GrupoRecurso(long id, String descricao, int ordem, boolean ativo) {
        public GrupoRecurso {
            Objects.requireNonNull(descricao, "descricao é obrigatória");
        }
    }

    /** Setor_Envolvido (ENVO) com Unidade_Macro obrigatória e e-mails alternativos opcionais. */
    public record Setor(long id, String descricao, String email, boolean ativo, String unidade,
                        List<String> emailsAlternativos) {
        public Setor {
            Objects.requireNonNull(descricao, "descricao é obrigatória");
            Objects.requireNonNull(unidade, "unidade é obrigatória");
            emailsAlternativos = emailsAlternativos == null ? List.of() : List.copyOf(emailsAlternativos);
        }
    }

    /**
     * Vínculo de Setor_Envolvido com Ambiente (EAMB) ou Recurso (EREC).
     *
     * @param id            EAMB_ID / EREC_ID
     * @param envoId        EAMB_ENVO_ID / EREC_ENVO_ID
     * @param alvoId        EAMB_AMBI_ID / EREC_RECU_ID
     * @param codServicoSnp código de serviço do SNP (opcional)
     */
    public record VinculoSetor(long id, long envoId, long alvoId, String codServicoSnp) {
    }

    /** Vínculo Recurso–Ambiente (VREC). */
    public record VinculoRecursoAmbiente(long id, long recursoId, long ambienteId) {
    }
}
