package br.com.mentoria.validacao.servico;

import br.com.mentoria.validacao.persistencia.ValidacaoRegistroRepository;
import br.com.mentoria.validacao.web.ValidacaoRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Nao depende do Tesseract instalado: cobre a configuracao, o fallback e a limpeza do arquivo temporario. */
class OcrServiceTest {

    private static ValidacaoDocumentoService servico(OcrService ocr) {
        return new ValidacaoDocumentoService(new InterpretadorIaService("", new ExtratorTexto()), new ValidadorRecibo(12),
                new CadastroBeneficiarios(),
                ocr, new OpenCvService(""), new PdfService(), mock(ValidacaoRegistroRepository.class));
    }

    @Test
    void soEstaConfiguradoComTessdataPath() {
        assertThat(new OcrService("tesseract", "", "por").configurado()).isFalse();
        assertThat(new OcrService("tesseract", null, "por").configurado()).isFalse();
        assertThat(new OcrService("tesseract", "C:/tesseract/tessdata", "por").configurado()).isTrue();
    }

    @Test
    void semOcrConfiguradoUsaConteudoDocumentoMesmoComImagem() {
        String imagem = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});

        var resposta = servico(new OcrService("tesseract", "", "por"))
                .validar(new ValidacaoRequest("recibo.png", "image/png", "Paciente: Maria Souza", imagem, null));

        assertThat(resposta.dadosExtraidos().nomePaciente()).isEqualTo("Maria Souza");
        assertThat(resposta.etapas().get(2).resultado()).startsWith("OCR nao configurado");
    }

    @Test
    void falhaDoTesseractCaiNoConteudoDocumento() {
        String imagem = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});

        var resposta = servico(new OcrService("comando-inexistente-idp", "C:/tessdata", "por"))
                .validar(new ValidacaoRequest("recibo.png", "image/png", "Paciente: Maria Souza", imagem, null));

        assertThat(resposta.dadosExtraidos().nomePaciente()).isEqualTo("Maria Souza");
        assertThat(resposta.etapas().get(2).resultado()).startsWith("Falha no OCR");
    }

    @Test
    void falhaDoTesseractViraErroEApagaOArquivoTemporario() throws IOException {
        var ocr = new OcrService("comando-inexistente-idp", "C:/tessdata", "por");
        long antes = arquivosTemporariosDoOcr();

        assertThatThrownBy(() -> ocr.extrairTexto(new byte[]{1, 2, 3})).isInstanceOf(IOException.class);

        assertThat(arquivosTemporariosDoOcr()).isEqualTo(antes);
    }

    private static long arquivosTemporariosDoOcr() throws IOException {
        try (Stream<Path> arquivos = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return arquivos.filter(p -> p.getFileName().toString().startsWith("idp-ocr-")).count();
        }
    }
}
