package br.mp.mpf.sisgares.exportacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.mp.mpf.sisgares.exportacao.Armazenamento.UrlAssinada;
import br.mp.mpf.sisgares.exportacao.ServicoExportacao.Grupo;
import br.mp.mpf.sisgares.exportacao.ServicoExportacao.Linha;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * Round-trip da exportação com dublê de S3 (sem conta AWS): grava o CSV gerado via
 * {@link ArmazenamentoS3} contra um {@link S3Client} de memória (Mockito) e relê o objeto
 * armazenado, confirmando que o CSV reparseado reproduz exatamente as linhas originais.
 *
 * <p><b>Validates: Requirements 17.4, 22.6</b>
 */
@ExtendWith(MockitoExtension.class)
class ArmazenamentoS3Test {

    private static final String BUCKET = "sisgares-exportacoes";
    private static final LocalDate DIA = LocalDate.of(2025, 3, 10);

    @Mock
    private S3Client s3;

    @Mock
    private S3Presigner presigner;

    /** Objetos "gravados" no bucket fictício: chave -> bytes do conteúdo. */
    private final Map<String, byte[]> bucket = new HashMap<>();

    private static Linha linha(long id, String ambiente, String finalidade, String solicitante, String recursos) {
        return new Linha(id, DIA.atTime(9, 0), DIA.atTime(10, 30), ambiente, finalidade, solicitante, recursos, false);
    }

    @Test
    void gravaEReleoCsvPreservandoAsLinhas() throws Exception {
        // Dados fictícios com separador, aspas e quebras de linha para exercitar o escape/round-trip
        List<Grupo> grupos = List.of(new Grupo(DIA, List.of(
                linha(1, "Sala \"A\"; bloco 2", "Reunião\ncom quebra", "sub-1", "Projetor (2), Notebook"),
                linha(2, "Auditório", "Aula", "sub-2", ""))));
        byte[] csvOriginal = GeradorCsv.gerar(grupos);

        prepararDubleS3();
        ArmazenamentoS3 armazenamento = new ArmazenamentoS3(s3, presigner, BUCKET);

        String chave = ArmazenamentoS3.chave("sub-1", Instant.parse("2025-03-10T12:00:00Z"), "csv");
        UrlAssinada url = armazenamento.gravar(chave, csvOriginal,
                ServicoExportacao.Formato.CSV.contentType(), "reservas-2025-03-10.csv");

        // URL pré-assinada devolvida pelo presigner (dublê)
        assertThat(url.url()).startsWith("https://");
        assertThat(url.expiraEm()).isNotNull();

        // O conteúdo gravado no S3 (dublê) é idêntico ao CSV gerado
        byte[] csvArmazenado = bucket.get(chave);
        assertThat(csvArmazenado).isEqualTo(csvOriginal);

        // Round-trip: relê os bytes do bucket e reparseia o CSV
        List<List<String>> registros = GeradorCsvTest.ler(new String(csvArmazenado, StandardCharsets.UTF_8));
        assertThat(registros).hasSize(3);
        assertThat(registros.get(0)).isEqualTo(GeradorCsv.CABECALHO);
        assertThat(registros.get(1)).containsExactly("2025-03-10", "09:00", "2025-03-10T10:30", "1",
                "Sala \"A\"; bloco 2", "Reunião\ncom quebra", "sub-1", "Projetor (2), Notebook", "nao");
        assertThat(registros.get(2)).containsExactly("2025-03-10", "09:00", "2025-03-10T10:30", "2",
                "Auditório", "Aula", "sub-2", "", "nao");
    }

    @Test
    void gravaComContentTypeEDisposicaoCorretos() throws Exception {
        prepararDubleS3();
        ArmazenamentoS3 armazenamento = new ArmazenamentoS3(s3, presigner, BUCKET);
        byte[] csv = GeradorCsv.gerar(List.of(new Grupo(DIA, List.of(linha(1, "Sala", "Reunião", "sub-1", "")))));

        armazenamento.gravar("exportacoes/abc/arquivo.csv", csv,
                ServicoExportacao.Formato.CSV.contentType(), "reservas-2025-03-10.csv");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(captor.capture(), any(RequestBody.class));
        PutObjectRequest pedido = captor.getValue();
        assertThat(pedido.bucket()).isEqualTo(BUCKET);
        assertThat(pedido.key()).isEqualTo("exportacoes/abc/arquivo.csv");
        assertThat(pedido.contentType()).isEqualTo("text/csv; charset=utf-8");
        assertThat(pedido.contentDisposition()).isEqualTo("attachment; filename=\"reservas-2025-03-10.csv\"");
    }

    /** Configura o dublê para armazenar o conteúdo do putObject e devolver uma URL pré-assinada. */
    private void prepararDubleS3() throws Exception {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenAnswer(invocacao -> {
                    PutObjectRequest pedido = invocacao.getArgument(0);
                    RequestBody corpo = invocacao.getArgument(1);
                    try (InputStream in = corpo.contentStreamProvider().newStream()) {
                        bucket.put(pedido.key(), in.readAllBytes());
                    }
                    return PutObjectResponse.builder().build();
                });

        // URL pré-assinada fictícia (sem rede): só precisa de url() e expiration()
        PresignedGetObjectRequest assinada = mock(PresignedGetObjectRequest.class);
        when(assinada.url()).thenReturn(
                URI.create("https://" + BUCKET + ".s3.amazonaws.com/exportacoes/abc/arquivo.csv").toURL());
        when(assinada.expiration()).thenReturn(Instant.parse("2025-03-10T12:05:00Z"));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(assinada);
    }
}
