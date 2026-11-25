package br.com.mentoria.validacao.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O analista decide os recibos que caem em revisao manual. */
@SpringBootTest
@AutoConfigureMockMvc
class RevisaoManualTest {

    private static final String ONTEM = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    private String validar(String conteudo) throws Exception {
        return mvc.perform(post("/api/documentos/validar").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "nomeArquivo", "recibo.pdf", "mimeType", "application/pdf", "conteudoDocumento", conteudo))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Location");
    }

    private ResultActions revisar(String location, String corpo) throws Exception {
        return mvc.perform(post(location + "/revisao").contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    @Test
    void analistaAprovaReciboEmRevisaoEOMotivoFicaSalvoSemCpfNemCnpj() throws Exception {
        String location = validar("Paciente: Ana Lima\nValor: R$ 100,00\nData do atendimento: " + ONTEM);

        revisar(location, "{\"decisao\":\"APROVADO\",\"motivo\":\"Conferi com a clinica 11.222.333/0001-81, CPF 529.982.247-25\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADO"))
                .andExpect(jsonPath("$.revisadoManualmente").value(true));

        JsonNode registro = json.readTree(mvc.perform(get(location)).andReturn().getResponse().getContentAsString());
        assertThat(registro.path("status").asText()).isEqualTo("APROVADO");
        assertThat(registro.path("motivoRevisao").asText())
                .isEqualTo("Conferi com a clinica **.***.***/****-**, CPF ***.***.***-**");
    }

    @Test
    void naoRevisaReciboQueNaoEstaEmRevisao() throws Exception {
        String location = validar("Paciente: Ana Lima\nCPF: 529.982.247-25\nCNPJ: 11.222.333/0001-81\nCRM-PE 12345\n"
                + "Data do atendimento: 01/01/2020\nValor: R$ 100,00");

        revisar(location, "{\"decisao\":\"APROVADO\",\"motivo\":\"ok\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("So recibos em revisao manual podem ser revisados"));
    }

    @Test
    void exigeDecisaoValidaEMotivo() throws Exception {
        String location = validar("Paciente: Ana Lima\nValor: R$ 100,00\nData do atendimento: " + ONTEM);

        revisar(location, "{\"decisao\":\"TALVEZ\",\"motivo\":\" \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros", hasItem("decisao deve ser APROVADO ou REPROVADO")))
                .andExpect(jsonPath("$.erros", hasItem("Informe o motivo da decisao")));
    }

    @Test
    void idInexistenteDa404() throws Exception {
        revisar("/api/documentos/999999", "{\"decisao\":\"REPROVADO\",\"motivo\":\"x\"}").andExpect(status().isNotFound());
    }
}
