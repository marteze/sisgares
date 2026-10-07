package br.mp.mpf.sisgares.seed;

import static org.assertj.core.api.Assertions.assertThat;

import br.mp.mpf.sisgares.comumaws.repositorio.ModelosCatalogo.GrupoRecurso;
import br.mp.mpf.sisgares.dominio.ResultadoIcone;
import br.mp.mpf.sisgares.seed.RelatorioCarga.Ocorrencia;
import br.mp.mpf.sisgares.seed.RelatorioCarga.RelatorioArquivo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Testes unitários do {@link ImportadorSeed} com os CSVs fictícios de {@code data/} (Tarefa 12.3).
 *
 * <p><b>Validates: Requirements 4.10, 4.11, 22.11</b>
 */
class ImportadorSeedTest {

    private static final String SNP_URL = "https://snp-simulado.exemplo.gov.br/api";

    /** Imagens que resolvem exatamente os ícones citados nos CSVs de {@code data/}. */
    private static List<String> imagensQueResolvem(Map<String, String> csvs) {
        List<String> imagens = new ArrayList<>();
        // Disposições disp_001.jpg .. disp_007.jpg
        for (int i = 1; i <= 7; i++) {
            imagens.add(String.format("icones-disposicao/disp_%03d_disposicao-ficticia.jpg", i));
        }
        // Recursos citados em dados-recurso.csv (serv_*, estrut_*, equip_*)
        for (String nome : List.of("serv_001", "serv_002", "serv_003", "estrut_001", "estrut_002",
                "equip_001", "equip_002", "equip_003", "equip_004", "equip_005", "equip_006",
                "equip_007", "equip_008", "equip_009", "equip_010", "equip_011")) {
            imagens.add("icones-recurso/" + nome + "_recurso-ficticio.png");
        }
        return imagens;
    }

    private static RelatorioArquivo arquivo(RelatorioCarga relatorio, String nome) {
        return relatorio.arquivos().stream().filter(a -> a.arquivo().equals(nome)).findFirst().orElseThrow();
    }

    @Test
    void cargaDosCsvsFicticiosGravaUnidadeConfiguracaoEUsuarios() {
        Map<String, String> csvs = CsvsSeed.carregar();
        DestinoMemoria destino = new DestinoMemoria();

        RelatorioCarga relatorio = new ImportadorSeed(destino, SNP_URL)
                .importar(csvs, imagensQueResolvem(csvs), null);

        assertThat(relatorio.unidade()).isEqualTo(ImportadorSeed.UNIDADE);
        assertThat(destino.itens).containsKey("UNID#" + ImportadorSeed.UNIDADE);
        assertThat(destino.itens).containsKey("CONFIG");
        // 3 solicitantes + 1 administrador + 1 atendente por Setor_Envolvido (4 setores no CSV)
        assertThat(relatorio.usuarios()).isEqualTo(3 + 1 + 4);
    }

    @Test
    void idComPontoDeMilharEhNormalizadoSemSeparador() {
        // GREC fictício com ID "14.207" (ponto de milhar); o restante vem de data/
        Map<String, String> csvs = new LinkedHashMap<>(CsvsSeed.carregar());
        csvs.put(ImportadorSeed.ARQ_GREC, """
                "GREC_ID","GREC_DESC","GREC_ORDEM","GREC_ST_ATIVO"
                14.207,Grupo com ponto de milhar,1,S
                """);
        DestinoMemoria destino = new DestinoMemoria();

        RelatorioCarga relatorio = new ImportadorSeed(destino, SNP_URL)
                .importar(csvs, imagensQueResolvem(csvs), null);

        // 14.207 -> 14207 (Requisito 22.11)
        assertThat(destino.itens).containsKey("GREC#14207");
        assertThat(((GrupoRecurso) destino.itens.get("GREC#14207")).id()).isEqualTo(14207L);
        assertThat(arquivo(relatorio, ImportadorSeed.ARQ_GREC).gravadas()).isEqualTo(1);
    }

    @Test
    void linhasInvalidasSaoRejeitadasComLinhaEMotivoNoRelatorio() {
        // GREC com uma linha de formato inválido (ST_ATIVO fora de S/N) e um ID inteiro inválido
        Map<String, String> csvs = new LinkedHashMap<>(CsvsSeed.carregar());
        csvs.put(ImportadorSeed.ARQ_GREC, """
                "GREC_ID","GREC_DESC","GREC_ORDEM","GREC_ST_ATIVO"
                1,Serviço,1,S
                2,Equipamento,2,X
                abc,Estrutura,3,S
                """);
        DestinoMemoria destino = new DestinoMemoria();

        RelatorioCarga relatorio = new ImportadorSeed(destino, SNP_URL)
                .importar(csvs, imagensQueResolvem(csvs), null);

        RelatorioArquivo grec = arquivo(relatorio, ImportadorSeed.ARQ_GREC);
        assertThat(grec.lidas()).isEqualTo(3);
        assertThat(grec.gravadas()).isEqualTo(1);
        assertThat(grec.rejeitadas()).isEqualTo(2);
        // Rejeições citam a linha física (base 1) e o motivo FORMATO_INVALIDO
        assertThat(grec.rejeicoes()).extracting(Ocorrencia::linha).containsExactly(3, 4);
        assertThat(grec.rejeicoes()).extracting(Ocorrencia::motivo)
                .containsOnly(br.mp.mpf.sisgares.dominio.csv.Rejeicao.FORMATO_INVALIDO);
    }

    @Test
    void idInexistenteGeraRejeicaoReferenciandoLinha() {
        // RECU apontando para grupo inexistente (RECU_GREC_ID = 999)
        Map<String, String> csvs = new LinkedHashMap<>(CsvsSeed.carregar());
        csvs.put(ImportadorSeed.ARQ_RECU, """
                "RECU_ID","RECU_DESC","RECU_GREC_ID","RECU_ST_LIMITADO","RECU_DISPONIBILIDADE","RECU_ST_ATIVO","RECU_ICONE_ARQUIVO"
                1,Serviço de Copa,1,N,0,S,serv_001.png
                2,Recurso órfão,999,N,0,S,serv_002.png
                """);
        DestinoMemoria destino = new DestinoMemoria();

        RelatorioCarga relatorio = new ImportadorSeed(destino, SNP_URL)
                .importar(csvs, imagensQueResolvem(csvs), null);

        RelatorioArquivo recu = arquivo(relatorio, ImportadorSeed.ARQ_RECU);
        assertThat(recu.gravadas()).isEqualTo(1);
        assertThat(recu.rejeitadas()).isEqualTo(1);
        assertThat(recu.rejeicoes()).singleElement()
                .satisfies(o -> {
                    assertThat(o.motivo()).isEqualTo(ImportadorSeed.ID_INEXISTENTE);
                    assertThat(o.linha()).isEqualTo(3);
                });
    }

    @Test
    void arquivoAusenteEhRegistradoComoRejeicaoDoArquivo() {
        Map<String, String> csvs = new LinkedHashMap<>(CsvsSeed.carregar());
        csvs.remove(ImportadorSeed.ARQ_VREC);
        DestinoMemoria destino = new DestinoMemoria();

        RelatorioCarga relatorio = new ImportadorSeed(destino, SNP_URL)
                .importar(csvs, imagensQueResolvem(csvs), null);

        RelatorioArquivo vrec = arquivo(relatorio, ImportadorSeed.ARQ_VREC);
        assertThat(vrec.rejeicoes()).singleElement().satisfies(o -> {
            assertThat(o.motivo()).isEqualTo(ImportadorSeed.ARQUIVO_AUSENTE);
            assertThat(o.linha()).isZero(); // linha 0 = arquivo inteiro
        });
    }

    @Test
    void iconeNaoEncontradoEAmbiguoGeramAlertaComIconePadrao() {
        Map<String, String> csvs = new LinkedHashMap<>(CsvsSeed.carregar());
        // DISP fictício: disp_001 resolve, disp_002 ambíguo, disp_003 não encontrado
        csvs.put(ImportadorSeed.ARQ_DISP, """
                "DISP_ID","DISP_DESC","DISP_ST_ATIVO","DISP_ICONE_ARQUIVO"
                1,Auditório,S,disp_001.jpg
                2,Espinha de peixe,S,disp_002.jpg
                3,Mesa única,S,disp_003.jpg
                """);
        List<String> imagens = new ArrayList<>();
        imagens.add("icones-disposicao/disp_001_auditorio.jpg");
        // Dois correspondentes de disp_002 -> ambíguo
        imagens.add("icones-disposicao/disp_002_opcao-a.jpg");
        imagens.add("icones-disposicao/disp_002_opcao-b.jpg");
        // Nenhum correspondente de disp_003 -> não encontrado
        DestinoMemoria destino = new DestinoMemoria();

        RelatorioCarga relatorio = new ImportadorSeed(destino, SNP_URL).importar(csvs, imagens, null);

        List<Ocorrencia> alertas = arquivo(relatorio, ImportadorSeed.ARQ_DISP).alertasIcone();
        assertThat(alertas).extracting(Ocorrencia::motivo).containsExactlyInAnyOrder(
                ResultadoIcone.Motivo.ICONE_AMBIGUO.name(),
                ResultadoIcone.Motivo.ICONE_NAO_ENCONTRADO.name());
        // As três disposições são gravadas (ícone padrão quando não resolve)
        assertThat(arquivo(relatorio, ImportadorSeed.ARQ_DISP).gravadas()).isEqualTo(3);
    }
}
