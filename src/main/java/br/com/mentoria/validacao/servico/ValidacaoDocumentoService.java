package br.com.mentoria.validacao.servico;

import br.com.mentoria.validacao.dominio.Beneficiario;
import br.com.mentoria.validacao.dominio.DadosRecibo;
import br.com.mentoria.validacao.dominio.EtapaPipeline;
import br.com.mentoria.validacao.dominio.ResultadoValidacao;
import br.com.mentoria.validacao.dominio.StatusValidacao;
import br.com.mentoria.validacao.web.ValidacaoRequest;
import br.com.mentoria.validacao.web.ValidacaoResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ValidacaoDocumentoService {

    private final ExtratorTexto extrator;
    private final ValidadorRecibo validador;
    private final CadastroBeneficiarios beneficiarios;

    public ValidacaoDocumentoService(ExtratorTexto extrator, ValidadorRecibo validador,
                                     CadastroBeneficiarios beneficiarios) {
        this.extrator = extrator;
        this.validador = validador;
        this.beneficiarios = beneficiarios;
    }

    public ValidacaoResponse validar(ValidacaoRequest request) {
        List<EtapaPipeline> etapas = new ArrayList<>();
        etapas.add(new EtapaPipeline(1, "Documento", "Entrada do arquivo para o pipeline",
                request.nomeArquivo()));
        etapas.add(new EtapaPipeline(2, "OpenCV", "Pre-processamento da imagem",
                "Nao executado: pre-processamento ainda nao configurado"));

        String texto = request.conteudoDocumento() == null ? "" : request.conteudoDocumento();
        etapas.add(new EtapaPipeline(3, "Tesseract", "Extracao do texto do documento",
                "Texto obtido de conteudoDocumento (" + texto.length() + " caracteres)"));

        DadosRecibo dados = extrator.extrair(texto);
        etapas.add(new EtapaPipeline(4, "IA", "Interpretacao do texto em dados estruturados",
                "Extracao local por expressoes regulares"));

        ResultadoValidacao resultado = aplicarRegras(dados, request.carteirinhaBeneficiario());
        etapas.add(new EtapaPipeline(5, "Regras de negocio", "Validacao do recibo para reembolso",
                resultado.status().name()));

        return new ValidacaoResponse(request.nomeArquivo(), etapas, dados, resultado);
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
}
