package br.mp.mpf.sisgares.reservas;

import br.mp.mpf.sisgares.dominio.Ambiente;
import br.mp.mpf.sisgares.dominio.Configuracao;
import br.mp.mpf.sisgares.dominio.Recurso;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Leitura de catálogo e Configuração usada pelo {@link ServicoReservas}. */
public interface PortaCatalogo {

    /** Todos os Ambientes (a árvore é montada no domínio por {@code ArvoreAmbientes}). */
    List<Ambiente> ambientes();

    Optional<Recurso> recurso(long id);

    /** ENVO_IDs vinculados ao Ambiente (EAMB). */
    Set<Long> setoresDoAmbiente(long ambienteId);

    /** ENVO_IDs vinculados ao Recurso (EREC). */
    Set<Long> setoresDoRecurso(long recursoId);

    Configuracao configuracao();
}
