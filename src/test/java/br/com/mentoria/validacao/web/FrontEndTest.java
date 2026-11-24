package br.com.mentoria.validacao.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O front-end e servido pelo proprio Spring Boot, na raiz. */
@SpringBootTest
@AutoConfigureMockMvc
class FrontEndTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void raizServeAPaginaDeValidacao() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"validar\"")))
                .andExpect(content().string(containsString("id=\"carteirinha\"")));
    }

    @Test
    void scriptNuncaInsereDadosDaApiComoHtml() throws Exception {
        mvc.perform(get("/app.js")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString(".innerHTML"))))
                .andExpect(content().string(not(containsString(".outerHTML"))));
    }
}
