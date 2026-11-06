package br.com.mentoria.validacao.servico;

import br.com.mentoria.validacao.dominio.DadosRecibo;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ExtratorTexto {

    private static final Pattern PACIENTE =
            Pattern.compile("(?im)^\\s*(?:nome\\s+do\\s+)?paciente\\s*:\\s*(.+?)\\s*$");
    private static final Pattern CPF_PACIENTE =
            Pattern.compile("(?im)^\\s*CPF(?:\\s+do\\s+paciente)?\\s*:\\s*(\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2})\\b");

    public DadosRecibo extrair(String texto) {
        String conteudo = texto == null ? "" : texto;
        return new DadosRecibo(
                primeiro(PACIENTE, conteudo),
                primeiro(CPF_PACIENTE, conteudo),
                null, null, null, null, null, null, null);
    }

    private static String primeiro(Pattern padrao, String texto) {
        Matcher m = padrao.matcher(texto);
        return m.find() ? m.group(1) : null;
    }
}
