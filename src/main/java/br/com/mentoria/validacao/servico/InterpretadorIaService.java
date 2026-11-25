package br.com.mentoria.validacao.servico;

import br.com.mentoria.validacao.dominio.Cnpj;
import br.com.mentoria.validacao.dominio.Cpf;
import br.com.mentoria.validacao.dominio.DadosRecibo;
import br.com.mentoria.validacao.dominio.Texto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * Interpreta o texto do recibo com a OpenAI, pedindo um JSON estruturado.
 * CPF, CNPJ, registro profissional e valor sao lidos localmente por regex: nao dependem da IA.
 */
@Service
public class InterpretadorIaService {

    private static final URI URL = URI.create("https://api.openai.com/v1/chat/completions");
    private static final String MODELO = "gpt-4o-mini";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final DateTimeFormatter DD_MM_AAAA =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final String INSTRUCOES = """
            Voce extrai dados de um recibo de consulta ou procedimento medico a partir do texto de OCR.
            Responda somente com um JSON com as chaves nomePaciente, prestador, servico, dataAtendimento e numeroRecibo.
            dataAtendimento no formato dd/MM/yyyy. Use null para o que nao estiver no texto. Nao invente valores.""";

    private final String apiKey;
    private final ExtratorTexto extrator;
    private final ObjectMapper json = new ObjectMapper();

    public InterpretadorIaService(@Value("${app.ai.openai.api-key:}") String apiKey, ExtratorTexto extrator) {
        this.apiKey = apiKey;
        this.extrator = extrator;
    }

    public boolean configurado() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** O texto vai para a IA com CPF e CNPJ mascarados. Sem chave, extrai tudo por regex. */
    public DadosRecibo interpretar(String texto) throws IOException {
        if (!configurado()) {
            return extrairLocal(texto);
        }
        String original = texto == null ? "" : texto;
        DadosRecibo local = extrator.extrair(original);
        JsonNode campos = json.readTree(chamarOpenAi(mascarar(original)));
        return new DadosRecibo(
                noTexto(texto(campos, "nomePaciente"), original),
                local.cpfPaciente(),
                noTexto(texto(campos, "prestador"), original),
                local.documentoPrestador(),
                local.registroProfissional(),
                noTexto(texto(campos, "servico"), original),
                data(noTexto(texto(campos, "dataAtendimento"), original)),
                local.valor(),
                noTexto(texto(campos, "numeroRecibo"), original));
    }

    /** Fallback sem IA: extracao local por palavra-chave e regex. */
    public DadosRecibo extrairLocal(String texto) {
        return extrator.extrair(texto);
    }

    /** CNPJ primeiro, para o padrao de CPF nao pegar parte dele. */
    static String mascarar(String texto) {
        return Cpf.mascarar(Cnpj.mascarar(texto));
    }

    /** Anti-alucinacao: so aceita o valor da IA se ele aparece no texto do documento. */
    static String noTexto(String valor, String texto) {
        return valor != null && Texto.normalizar(texto).contains(Texto.normalizar(valor)) ? valor : null;
    }

    private String chamarOpenAi(String textoMascarado) throws IOException {
        ObjectNode corpo = json.createObjectNode().put("model", MODELO).put("temperature", 0);
        corpo.putObject("response_format").put("type", "json_object");
        corpo.putArray("messages")
                .add(json.createObjectNode().put("role", "system").put("content", INSTRUCOES))
                .add(json.createObjectNode().put("role", "user").put("content", textoMascarado));
        HttpRequest requisicao = HttpRequest.newBuilder(URL)
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)))
                .build();
        HttpResponse<String> resposta;
        try (HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            resposta = http.send(requisicao, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Chamada a OpenAI interrompida", e);
        }
        if (resposta.statusCode() != 200) {
            throw new IOException("OpenAI respondeu com status " + resposta.statusCode());
        }
        return json.readTree(resposta.body()).path("choices").path(0).path("message").path("content").asText("{}");
    }

    private static String texto(JsonNode campos, String chave) {
        JsonNode valor = campos.path(chave);
        return valor.isValueNode() && !valor.isNull() && !valor.asText().isBlank() ? valor.asText().trim() : null;
    }

    private static LocalDate data(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return LocalDate.parse(valor, DD_MM_AAAA);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
