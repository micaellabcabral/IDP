package br.com.mentoria.validacao.servico;

import br.com.mentoria.validacao.dominio.Beneficiario;
import br.com.mentoria.validacao.dominio.DadosRecibo;
import br.com.mentoria.validacao.dominio.EtapaPipeline;
import br.com.mentoria.validacao.dominio.ResultadoValidacao;
import br.com.mentoria.validacao.dominio.StatusValidacao;
import br.com.mentoria.validacao.persistencia.ValidacaoRegistro;
import br.com.mentoria.validacao.persistencia.ValidacaoRegistroRepository;
import br.com.mentoria.validacao.dominio.DadosGuardados;
import br.com.mentoria.validacao.web.RegistroBanco;
import br.com.mentoria.validacao.web.RevisaoRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import br.com.mentoria.validacao.web.ValidacaoRequest;
import br.com.mentoria.validacao.web.ValidacaoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Orquestra o pipeline: documento, pre-processamento, OCR, interpretacao e regras.
 * Se PDF, OpenCV ou Tesseract falharem (erro ou timeout), a etapa cai no fallback local em vez de derrubar a requisicao.
 */
@Service
public class ValidacaoDocumentoService {

    private static final Logger log = LoggerFactory.getLogger(ValidacaoDocumentoService.class);
    // BigDecimal lido como BigDecimal (e nao double): R$ 90,00 continua 90.00, sem perder casas
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private final InterpretadorIaService interpretador;
    private final ValidadorRecibo validador;
    private final CadastroBeneficiarios beneficiarios;
    private final OcrService ocr;
    private final OpenCvService openCv;
    private final PdfService pdf;
    private final ValidacaoRegistroRepository registros;

    public ValidacaoDocumentoService(InterpretadorIaService interpretador, ValidadorRecibo validador,
                                     CadastroBeneficiarios beneficiarios, OcrService ocr, OpenCvService openCv,
                                     PdfService pdf, ValidacaoRegistroRepository registros) {
        this.interpretador = interpretador;
        this.validador = validador;
        this.beneficiarios = beneficiarios;
        this.ocr = ocr;
        this.openCv = openCv;
        this.pdf = pdf;
        this.registros = registros;
    }

    public Optional<ValidacaoRegistro> buscar(Long id) {
        return registros.findById(id);
    }

    /**
     * Registra a decisao do analista. Vazio se o id nao existe; RevisaoNaoPermitidaException se o recibo
     * nao esta em revisao manual. CPF e CNPJ digitados no motivo sao mascarados antes de salvar.
     */
    @Transactional
    public Optional<ValidacaoRegistro> revisar(Long id, RevisaoRequest revisao) {
        return registros.findById(id).map(registro -> {
            registro.revisar(StatusValidacao.valueOf(revisao.decisao()),
                    InterpretadorIaService.mascarar(revisao.motivo().strip()), Instant.now());
            return registros.save(registro);
        });
    }

    /** Todos os registros com os dados do recibo abertos, do mais recente ao mais antigo (tela Banco de Dados). */
    public List<RegistroBanco> listarBanco() {
        return registros.findAllByOrderByCriadoEmDescIdDesc().stream().map(ValidacaoDocumentoService::paraBanco).toList();
    }

    private static RegistroBanco paraBanco(ValidacaoRegistro r) {
        DadosGuardados d = lerDados(r.dadosExtraidos());
        return new RegistroBanco(r.getId(), r.getNomeArquivo(), r.getStatus(), r.isAprovado(),
                d.regrasAtendidas(), r.getPendencias(),
                r.getCriadoEm(), r.isRevisadoManualmente(), r.getMotivoRevisao(), r.getRevisadoEm(),
                d.carteirinhaBeneficiario(), d.recibo(), d.etapas());
    }

    private static String json(DadosGuardados dados) {
        try {
            return JSON.writeValueAsString(dados);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static DadosGuardados lerDados(String json) {
        if (json == null) {
            return new DadosGuardados(null, null, null, null); // registros gravados antes de os dados serem guardados
        }
        try {
            return JSON.readValue(json, DadosGuardados.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** As 20 validacoes mais recentes, da mais nova para a mais antiga. */
    public List<ValidacaoRegistro> listarRecentes() {
        return registros.findTop20ByOrderByCriadoEmDescIdDesc();
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

        DadosRecibo dados;
        String resultadoIa = "Chave da IA nao configurada: extracao local por regex";
        if (interpretador.configurado()) {
            try {
                dados = interpretador.interpretar(texto);
                resultadoIa = "Interpretado pela OpenAI (CPF e CNPJ mascarados antes do envio)";
            } catch (IOException | RuntimeException e) {
                falha("IA", e);
                dados = interpretador.extrairLocal(texto);
                resultadoIa = "Falha na IA: extracao local por regex";
            }
        } else {
            dados = interpretador.extrairLocal(texto);
        }
        ResultadoValidacao resultado = aplicarRegras(dados, request.carteirinhaBeneficiario());
        String chave = chaveRecibo(dados);
        if (chave != null && registros.existsByChaveReciboAndStatusNot(chave, StatusValidacao.REPROVADO)) {
            List<String> pendencias = new ArrayList<>(resultado.pendencias());
            pendencias.add("Recibo ja enviado anteriormente (mesmo numero e mesmo prestador)");
            resultado = new ResultadoValidacao(false, StatusValidacao.REPROVADO, resultado.regrasAtendidas(), pendencias);
        }
        var registro = new ValidacaoRegistro(request.nomeArquivo(), resultado, chave, Instant.now());

        List<EtapaPipeline> etapas = List.of(
                new EtapaPipeline(1, "Documento", "Entrada do arquivo para o pipeline", request.nomeArquivo()),
                new EtapaPipeline(2, "OpenCV", "Pre-processamento da imagem", resultadoOpenCv),
                new EtapaPipeline(3, "Tesseract", "Extracao do texto do documento", resultadoOcr),
                new EtapaPipeline(4, "IA", "Interpretacao do texto em dados estruturados", resultadoIa),
                new EtapaPipeline(5, "Regras de negocio", "Validacao do recibo para reembolso",
                        resultado.status().name()));
        // guarda tudo o que a tela mostra (dados, regras atendidas e etapas) para o JSON de migracao sair completo
        registro.guardarDados(json(new DadosGuardados(dados, request.carteirinhaBeneficiario(),
                resultado.regrasAtendidas(), etapas)));
        registros.save(registro); // registro novo: o persist preenche o id na propria instancia

        return new ValidacaoResponse(registro.getId(), request.nomeArquivo(), etapas, dados, resultado);
    }

    /** SHA-256 de "numero|documento do prestador (so digitos)": compara recibos sem guardar os dados. */
    static String chaveRecibo(DadosRecibo dados) {
        if (dados.numeroRecibo() == null || dados.documentoPrestador() == null) {
            return null;
        }
        String base = dados.numeroRecibo().trim() + "|" + dados.documentoPrestador().replaceAll("\\D", "");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(base.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
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
