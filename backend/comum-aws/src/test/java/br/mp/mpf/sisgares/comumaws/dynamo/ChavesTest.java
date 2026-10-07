package br.mp.mpf.sisgares.comumaws.dynamo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Testes de exemplo das convenções de chaves da tabela {@code sisgares}.
 *
 * <p><b>Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.7, 3.8, 23.3</b>
 */
class ChavesTest {

    @Test
    void montaChavesConformeDesign() {
        assertThat(Chaves.pkAmbiente(10L)).isEqualTo("AMBI#10");
        assertThat(Chaves.pkReserva(7L)).isEqualTo("RESE#7");
        assertThat(Chaves.skPeriodo(3L)).isEqualTo("PRES#3");
        assertThat(Chaves.pkControleAmbiente(1L)).isEqualTo("CTRL#AMBI#1");
        assertThat(Chaves.pkControleRecurso(2L)).isEqualTo("CTRL#RECU#2");
        assertThat(Chaves.skVersao(1)).isEqualTo("VERS#0001");
        assertThat(Chaves.skNotificacao("ev1", 5L)).isEqualTo("NOTI#ev1#5");
        assertThat(Chaves.gsi1Pk(1L)).isEqualTo("RAIZ#1");
        assertThat(Chaves.gsi4Pk("abc")).isEqualTo("SOLIC#abc");
    }

    @Test
    void skDeDataTemLarguraFixaEOrdemCronologica() {
        String a = Chaves.skDataReserva(LocalDateTime.of(2025, 3, 1, 9, 0), 2L);
        String b = Chaves.skDataReserva(LocalDateTime.of(2025, 3, 1, 9, 0, 30), 1L);
        assertThat(a).isEqualTo("2025-03-01T09:00:00#2");
        assertThat(a).isLessThan(b);
        assertThat(Chaves.limiteSuperior(Chaves.data(LocalDateTime.of(2025, 3, 1, 9, 0)))).isGreaterThan(a);
    }

    @Test
    void rejeitaIdentificadorNuloOuVazio() {
        assertThatThrownBy(() -> Chaves.pkReserva(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Chaves.pkUsuario(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exigeNomeDaTabelaNoAmbiente() {
        assertThatThrownBy(() -> ClienteDynamo.lerNomeTabela(Map.<String, String>of()::get))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ClienteDynamo.lerNomeTabela(Map.of("TABELA_SISGARES", "sisgares")::get)).isEqualTo("sisgares");
    }
}
