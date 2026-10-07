package br.mp.mpf.sisgares.eventos.notificador;

import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.Setor;
import java.util.List;
import java.util.Optional;

/**
 * Leitura dos vínculos EAMB/EREC e dos Setores_Envolvidos usada pelo {@link ResolvedorDestinatarios}.
 */
public interface FonteSetores {

    /** IDs dos setores vinculados ao Ambiente (EAMB). */
    List<Long> setoresDoAmbiente(long ambienteId);

    /** IDs dos setores vinculados ao Recurso (EREC). */
    List<Long> setoresDoRecurso(long recursoId);

    /** Setor por ID. */
    Optional<Setor> setor(long envoId);
}
