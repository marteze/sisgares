package br.mp.mpf.sisgares.configuracao;

import br.mp.mpf.sisgares.comumaws.repositorio.RepositorioConfiguracao;
import br.mp.mpf.sisgares.dominio.Configuracao;
import java.util.Objects;
import java.util.Optional;

/**
 * Porta de persistência da Configuração usada pelo {@link Handler}; permite testes em memória.
 */
interface ArmazemConfiguracao {

    /** Configuração vigente e URL do SNP (pode ser null). */
    record Vigente(Configuracao configuracao, String snpUrl) {
        public Vigente {
            Objects.requireNonNull(configuracao, "configuracao");
        }
    }

    /** Configuração vigente ou vazio se a configuração global ainda não foi cadastrada. */
    Optional<Vigente> obter();

    /** Grava a configuração e a URL do SNP. */
    void salvar(Configuracao configuracao, String snpUrl);

    /** Adaptador para o {@link RepositorioConfiguracao} (DynamoDB). */
    static ArmazemConfiguracao de(RepositorioConfiguracao repositorio) {
        Objects.requireNonNull(repositorio, "repositorio");
        return new ArmazemConfiguracao() {
            @Override
            public Optional<Vigente> obter() {
                try {
                    return Optional.of(new Vigente(repositorio.obter(), repositorio.snpUrl().orElse(null)));
                } catch (IllegalStateException e) {
                    // O repositório sinaliza ausência do item GLOBAL com IllegalStateException
                    return Optional.empty();
                }
            }

            @Override
            public void salvar(Configuracao configuracao, String snpUrl) {
                repositorio.salvar(configuracao, snpUrl);
            }
        };
    }
}
