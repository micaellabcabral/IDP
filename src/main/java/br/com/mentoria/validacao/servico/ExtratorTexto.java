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
    private static final Pattern PRESTADOR =
            Pattern.compile("(?im)^\\s*(?:nome\\s+do\\s+)?(?:prestador|profissional)\\s*:\\s*(.+?)\\s*$");
    private static final Pattern DOCUMENTO_PRESTADOR =
            Pattern.compile("(?im)^\\s*(?:CPF/CNPJ|CNPJ|CPF\\s+do\\s+prestador)[^:\\r\\n]*:\\s*"
                    + "(\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}|\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2})\\b");
    private static final Pattern REGISTRO_PROFISSIONAL =
            Pattern.compile("(?i)\\b((?:CRM|CRO)[ \\t/-]*[A-Z]{2}[ \\t/-]*\\d{4,6}|CRP[ \\t/-]*\\d{2}[ \\t/-]*\\d{4,6})\\b");

    public DadosRecibo extrair(String texto) {
        String conteudo = texto == null ? "" : texto;
        return new DadosRecibo(
                primeiro(PACIENTE, conteudo),
                primeiro(CPF_PACIENTE, conteudo),
                primeiro(PRESTADOR, conteudo),
                primeiro(DOCUMENTO_PRESTADOR, conteudo),
                primeiro(REGISTRO_PROFISSIONAL, conteudo),
                null, null, null, null);
    }

    private static String primeiro(Pattern padrao, String texto) {
        Matcher m = padrao.matcher(texto);
        return m.find() ? m.group(1) : null;
    }
}
