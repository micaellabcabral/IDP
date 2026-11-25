package br.com.mentoria.validacao.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo real (sem mocks): busca o recibo de exemplo e o envia para validacao. */
@SpringBootTest
@AutoConfigureMockMvc
class ValidacaoExemploTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void reciboDeExemploEAprovado() throws Exception {
        String exemplo = mvc.perform(get("/api/documentos/exemplo"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // o exemplo usa sempre um atendimento de 10 dias atras, entao e aprovado em qualquer data
        mvc.perform(post("/api/documentos/validar").contentType(MediaType.APPLICATION_JSON).content(exemplo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeArquivo").value("recibo-consulta.pdf"))
                .andExpect(jsonPath("$.etapas", hasSize(5)))
                .andExpect(jsonPath("$.dadosExtraidos.nomePaciente").value("Joao da Silva"))
                .andExpect(jsonPath("$.dadosExtraidos.cpfPaciente").value("529.982.247-25"))
                .andExpect(jsonPath("$.dadosExtraidos.registroProfissional").value("CRM-SP 123456"))
                .andExpect(jsonPath("$.dadosExtraidos.documentoPrestador").value("11.222.333/0001-81"))
                .andExpect(jsonPath("$.dadosExtraidos.dataAtendimento").value(LocalDate.now().minusDays(10).toString()))
                .andExpect(jsonPath("$.dadosExtraidos.numeroRecibo").value("1024"))
                .andExpect(jsonPath("$.resultado.status").value("APROVADO"))
                .andExpect(jsonPath("$.resultado.pendencias", empty()))
                .andExpect(jsonPath("$.resultado.regrasAtendidas", hasItem("Paciente confere com o beneficiario")));
    }
}
