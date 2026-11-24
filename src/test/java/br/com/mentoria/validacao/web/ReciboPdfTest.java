package br.com.mentoria.validacao.web;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Um recibo em PDF com texto e lido direto pelo PDFBox, sem precisar do Tesseract. */
@SpringBootTest
@AutoConfigureMockMvc
class ReciboPdfTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void reciboEmPdfComTextoELidoSemOcr() throws Exception {
        String data = LocalDate.now().minusDays(3).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String pdf = Base64.getEncoder().encodeToString(pdfCom(
                "RECIBO DE CONSULTA MEDICA", "Numero do recibo: 2048", "Paciente: Maria Souza",
                "CPF: 123.456.789-09", "Profissional: Dr. Carlos Lima", "CRM-PE 12345",
                "CNPJ: 11.222.333/0001-81", "Data do atendimento: " + data, "Valor: R$ 280,00"));
        String corpo = "{\"nomeArquivo\":\"recibo.pdf\",\"mimeType\":\"application/pdf\",\"imagemBase64\":\"" + pdf
                + "\",\"carteirinhaBeneficiario\":\"0001234567\"}";

        mvc.perform(post("/api/documentos/validar").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapas[2].resultado", startsWith("Texto lido direto do PDF")))
                .andExpect(jsonPath("$.dadosExtraidos.nomePaciente").value("Maria Souza"))
                .andExpect(jsonPath("$.dadosExtraidos.numeroRecibo").value("2048"))
                .andExpect(jsonPath("$.dadosExtraidos.valor").value(280.00))
                .andExpect(jsonPath("$.resultado.status").value("APROVADO"));
    }

    static byte[] pdfCom(String... linhas) throws IOException {
        try (var documento = new PDDocument(); var saida = new ByteArrayOutputStream()) {
            var pagina = new PDPage();
            documento.addPage(pagina);
            try (var conteudo = new PDPageContentStream(documento, pagina)) {
                conteudo.beginText();
                conteudo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                conteudo.setLeading(16);
                conteudo.newLineAtOffset(50, 720);
                for (String linha : linhas) {
                    conteudo.showText(linha);
                    conteudo.newLine();
                }
                conteudo.endText();
            }
            documento.save(saida);
            return saida.toByteArray();
        }
    }
}
