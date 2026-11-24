package br.com.mentoria.validacao.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Resultado salvo sem dados pessoais, consulta por id, historico e recibo duplicado. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PersistenciaTest {

    @Autowired
    private MockMvc mvc;

    private String exemplo() throws Exception {
        return mvc.perform(get("/api/documentos/exemplo")).andReturn().getResponse().getContentAsString();
    }

    @Test
    void salvaOResultadoEConsultaPeloLocation() throws Exception {
        String location = mvc.perform(post("/api/documentos/validar").contentType(MediaType.APPLICATION_JSON).content(exemplo()))
                .andExpect(status().isOk())
                .andExpect(header().string("Location", containsString("/api/documentos/")))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADO"))
                .andExpect(jsonPath("$.nomeArquivo").value("recibo-consulta.pdf"))
                .andExpect(content().string(not(containsString("529.982.247-25"))))
                .andExpect(content().string(not(containsString("Joao da Silva"))))
                .andExpect(content().string(not(containsString("chaveRecibo"))));

        mvc.perform(get("/api/documentos/999999")).andExpect(status().isNotFound());
    }

    @Test
    void mesmoReciboEnviadoDuasVezesEReprovadoNaSegunda() throws Exception {
        String recibo = exemplo();
        mvc.perform(post("/api/documentos/validar").contentType(MediaType.APPLICATION_JSON).content(recibo))
                .andExpect(jsonPath("$.resultado.status").value("APROVADO"));

        mvc.perform(post("/api/documentos/validar").contentType(MediaType.APPLICATION_JSON).content(recibo))
                .andExpect(jsonPath("$.resultado.status").value("REPROVADO"))
                .andExpect(jsonPath("$.resultado.pendencias",
                        hasItem("Recibo ja enviado anteriormente (mesmo numero e mesmo prestador)")));
    }

    @Test
    void historicoListaAsValidacoesSemDadosPessoais() throws Exception {
        mvc.perform(post("/api/documentos/validar").contentType(MediaType.APPLICATION_JSON).content(exemplo()));

        mvc.perform(get("/api/documentos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nomeArquivo", hasItem("recibo-consulta.pdf")))
                .andExpect(content().string(not(containsString("529.982.247-25"))))
                .andExpect(content().string(not(containsString("11.222.333/0001-81"))));
    }
}
