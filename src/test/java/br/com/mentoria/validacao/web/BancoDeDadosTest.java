package br.com.mentoria.validacao.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Dados do recibo: abertos na tela Banco de Dados e no JSON, cifrados na coluna do banco. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BancoDeDadosTest {

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private long validarExemplo() throws Exception {
        String exemplo = mvc.perform(get("/api/documentos/exemplo")).andReturn().getResponse().getContentAsString();
        String location = mvc.perform(post("/api/documentos/validar").contentType(MediaType.APPLICATION_JSON).content(exemplo))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    private static JsonNode registro(JsonNode lista, long id) {
        for (JsonNode r : lista) {
            if (r.path("id").asLong() == id) {
                return r;
            }
        }
        throw new AssertionError("registro " + id + " nao listado");
    }

    @Test
    void bancoMostraOsDadosDoReciboEACarteirinha() throws Exception {
        long id = validarExemplo();

        JsonNode r = registro(json.readTree(mvc.perform(get("/api/banco")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()), id);

        assertThat(r.path("status").asText()).isEqualTo("APROVADO");
        assertThat(r.path("carteirinhaBeneficiario").asText()).isEqualTo("0007654321");
        assertThat(r.path("recibo").path("nomePaciente").asText()).isEqualTo("Joao da Silva");
        assertThat(r.path("recibo").path("documentoPrestador").asText()).isEqualTo("11.222.333/0001-81");
        assertThat(r.path("recibo").path("valor").decimalValue()).isEqualByComparingTo("350.00");
    }

    @Test
    void colunaNoBancoFicaCifradaEHistoricoSegueSemDadosPessoais() throws Exception {
        long id = validarExemplo();

        String coluna = jdbc.queryForObject("select dados_extraidos from validacao_registro where id = ?", String.class, id);
        assertThat(coluna).doesNotContain("Joao", "529.982.247-25", "11.222.333/0001-81", "0007654321");

        String historico = mvc.perform(get("/api/documentos")).andReturn().getResponse().getContentAsString();
        String consulta = mvc.perform(get("/api/documentos/" + id)).andReturn().getResponse().getContentAsString();
        assertThat(historico).doesNotContain("Joao", "529.982.247-25");
        assertThat(consulta).doesNotContain("Joao", "dadosExtraidos");
    }

    @Test
    void exportaJsonComoArquivoParaMigracao() throws Exception {
        long id = validarExemplo();

        String corpo = mvc.perform(get("/api/banco/exportar"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment; filename=\"idp-reembolsos-")))
                .andReturn().getResponse().getContentAsString();
        JsonNode exportacao = json.readTree(corpo);

        assertThat(exportacao.path("total").asInt()).isEqualTo(exportacao.path("registros").size());
        assertThat(registro(exportacao.path("registros"), id).path("recibo").path("dataAtendimento").asText()).hasSize(10);
    }
}
