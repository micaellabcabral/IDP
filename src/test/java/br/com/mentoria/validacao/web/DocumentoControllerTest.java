package br.com.mentoria.validacao.web;

import br.com.mentoria.validacao.dominio.DadosRecibo;
import br.com.mentoria.validacao.dominio.EtapaPipeline;
import br.com.mentoria.validacao.dominio.ResultadoValidacao;
import br.com.mentoria.validacao.dominio.StatusValidacao;
import br.com.mentoria.validacao.servico.ValidacaoDocumentoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentoController.class)
class DocumentoControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ValidacaoDocumentoService service;

    @Test
    void exemploRetornaUmReciboDeConsulta() throws Exception {
        mvc.perform(get("/api/documentos/exemplo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeArquivo").value("recibo-consulta.pdf"))
                .andExpect(jsonPath("$.mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.carteirinhaBeneficiario").value("0007654321"))
                .andExpect(jsonPath("$.conteudoDocumento").isNotEmpty());
    }

    @Test
    void validarRetornaEtapasDadosEResultado() throws Exception {
        var dados = new DadosRecibo("Joao da Silva", "529.982.247-25", "Dra. Ana Paula Ribeiro", "11.222.333/0001-81",
                "CRM-SP 123456", "Consulta medica", LocalDate.of(2026, 11, 1), new BigDecimal("350.00"), "1024");
        var resultado = new ResultadoValidacao(true, StatusValidacao.APROVADO, List.of("Paciente identificado"), List.of());
        when(service.validar(any())).thenReturn(new ValidacaoResponse(null, "recibo.pdf",
                List.of(new EtapaPipeline(1, "Documento", "Entrada do arquivo para o pipeline", "recibo.pdf")),
                dados, resultado));

        mvc.perform(post("/api/documentos/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nomeArquivo\":\"recibo.pdf\",\"mimeType\":\"application/pdf\",\"conteudoDocumento\":\"texto\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeArquivo").value("recibo.pdf"))
                .andExpect(jsonPath("$.etapas[0].ordem").value(1))
                .andExpect(jsonPath("$.dadosExtraidos.registroProfissional").value("CRM-SP 123456"))
                .andExpect(jsonPath("$.dadosExtraidos.valor").value(350.00))
                .andExpect(jsonPath("$.resultado.aprovado").value(true))
                .andExpect(jsonPath("$.resultado.status").value("APROVADO"));
    }
}
