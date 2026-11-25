package br.com.mentoria.validacao.servico;

import br.com.mentoria.validacao.dominio.Beneficiario;
import br.com.mentoria.validacao.dominio.DadosRecibo;
import br.com.mentoria.validacao.dominio.EtapaPipeline;
import br.com.mentoria.validacao.dominio.ResultadoValidacao;
import br.com.mentoria.validacao.dominio.StatusValidacao;
import br.com.mentoria.validacao.web.ValidacaoRequest;
import br.com.mentoria.validacao.web.ValidacaoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Orquestra o pipeline: documento, pre-processamento, OCR, interpretacao e regras.
 * Se PDF, OpenCV ou Tesseract falharem (erro ou timeout), a etapa cai no fallback local em vez de derrubar a requisicao.
 */
@Service
public class ValidacaoDocumentoService {

    private static final Logger log = LoggerFactory.getLogger(ValidacaoDocumentoService.class);

    private final ExtratorTexto extrator;
    private final ValidadorRecibo validador;
    private final CadastroBeneficiarios beneficiarios;
    private final OcrService ocr;
    private final OpenCvService openCv;
    private final PdfService pdf;

    public ValidacaoDocumentoService(ExtratorTexto extrator, ValidadorRecibo validador,
                                     CadastroBeneficiarios beneficiarios, OcrService ocr, OpenCvService openCv,
                                     PdfService pdf) {
        this.extrator = extrator;
        this.validador = validador;
        this.beneficiarios = beneficiarios;
        this.ocr = ocr;
        this.openCv = openCv;
        this.pdf = pdf;
    }

    public ValidacaoResponse validar(ValidacaoRequest request) {
        String conteudo = request.conteudoDocumento() == null ? "" : request.conteudoDocumento();
        boolean temImagem = request.imagemBase64() != null && !request.imagemBase64().isBlank();
        String texto = conteudo;
        String resultadoOpenCv = "Nao executado";
        String resultadoOcr = "OCR nao configurado: usando conteudoDocumento (" + conteudo.length() + " caracteres)";

        byte[] imagem = temImagem ? request.decodificarImagem() : null;
        if (imagem != null && "application/pdf".equals(request.mimeType())) {
            try {
                String textoPdf = pdf.extrairTexto(imagem).strip();
                if (textoPdf.isEmpty()) {
                    imagem = pdf.primeiraPaginaComoImagem(imagem);
                } else {
                    texto = textoPdf;
                    resultadoOcr = "Texto lido direto do PDF (" + texto.length() + " caracteres), sem OCR";
                    imagem = null;
                }
            } catch (IOException | RuntimeException e) {
                falha("PDF", e);
                resultadoOcr = "Falha ao ler o PDF: usando conteudoDocumento (" + conteudo.length() + " caracteres)";
                imagem = null;
            }
        }

        if (imagem != null && ocr.configurado()) {
            if (openCv.configurado()) {
                try {
                    imagem = openCv.preprocessar(imagem);
                    resultadoOpenCv = "Imagem tratada pelo OpenCV";
                } catch (IOException | RuntimeException e) {
                    falha("OpenCV", e);
                    resultadoOpenCv = "Falha no OpenCV: usando a imagem original";
                }
            }
            try {
                texto = ocr.extrairTexto(imagem);
                resultadoOcr = texto.length() + " caracteres extraidos da imagem";
            } catch (IOException | RuntimeException e) {
                falha("Tesseract", e);
                resultadoOcr = "Falha no OCR: usando conteudoDocumento (" + conteudo.length() + " caracteres)";
            }
        }

        DadosRecibo dados = extrator.extrair(texto);
        ResultadoValidacao resultado = aplicarRegras(dados, request.carteirinhaBeneficiario());

        List<EtapaPipeline> etapas = List.of(
                new EtapaPipeline(1, "Documento", "Entrada do arquivo para o pipeline", request.nomeArquivo()),
                new EtapaPipeline(2, "OpenCV", "Pre-processamento da imagem", resultadoOpenCv),
                new EtapaPipeline(3, "Tesseract", "Extracao do texto do documento", resultadoOcr),
                new EtapaPipeline(4, "IA", "Interpretacao do texto em dados estruturados",
                        "Extracao local por expressoes regulares"),
                new EtapaPipeline(5, "Regras de negocio", "Validacao do recibo para reembolso",
                        resultado.status().name()));

        return new ValidacaoResponse(null, request.nomeArquivo(), etapas, dados, resultado);
    }

    private ResultadoValidacao aplicarRegras(DadosRecibo dados, String carteirinha) {
        LocalDate hoje = LocalDate.now();
        if (carteirinha == null || carteirinha.isBlank()) {
            return validador.validar(dados, hoje);
        }
        Beneficiario beneficiario = beneficiarios.buscarPorCarteirinha(carteirinha).orElse(null);
        if (beneficiario != null) {
            return validador.validar(dados, hoje, beneficiario);
        }
        ResultadoValidacao base = validador.validar(dados, hoje);
        List<String> pendencias = new ArrayList<>(base.pendencias());
        pendencias.add("Beneficiario nao encontrado para a carteirinha informada");
        return new ResultadoValidacao(false, StatusValidacao.REPROVADO,
                new ArrayList<>(base.regrasAtendidas()), pendencias);
    }

    private static void falha(String etapa, Exception e) {
        // so o tipo da excecao: a mensagem pode conter trechos do documento
        log.warn("Falha na etapa {} ({}); usando fallback local", etapa, e.getClass().getSimpleName());
    }
}
