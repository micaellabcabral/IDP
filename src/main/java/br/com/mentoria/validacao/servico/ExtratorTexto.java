package br.com.mentoria.validacao.servico;

import br.com.mentoria.validacao.dominio.DadosRecibo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
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
    private static final Pattern DATA_ATENDIMENTO =
            Pattern.compile("(?im)^\\s*data(?:\\s+do\\s+atendimento)?\\s*:\\s*(\\d{2})/(\\d{2})/(\\d{4})\\b");
    private static final Pattern VALOR =
            Pattern.compile("(?i)R\\$\\s*(\\d{1,3}(?:\\.\\d{3})+,\\d{2}|\\d+,\\d{2})");
    private static final Pattern NUMERO_RECIBO =
            Pattern.compile("(?iu)\\brecibo\\s*(?:n(?:\u00ba|\u00b0|o|\\.|\u00famero|umero)?\\s*)?:?\\s*(\\d+)\\b"
                    + "|\\bn(?:\u00fa|u)mero\\s+do\\s+recibo\\s*:?\\s*(\\d+)\\b");

    public DadosRecibo extrair(String texto) {
        String conteudo = texto == null ? "" : texto;
        return new DadosRecibo(
                primeiro(PACIENTE, conteudo),
                primeiro(CPF_PACIENTE, conteudo),
                primeiro(PRESTADOR, conteudo),
                primeiro(DOCUMENTO_PRESTADOR, conteudo),
                primeiro(REGISTRO_PROFISSIONAL, conteudo),
                null,
                data(conteudo),
                valor(conteudo),
                numeroRecibo(conteudo));
    }

    private static String primeiro(Pattern padrao, String texto) {
        Matcher m = padrao.matcher(texto);
        return m.find() ? m.group(1) : null;
    }

    private static LocalDate data(String texto) {
        Matcher m = DATA_ATENDIMENTO.matcher(texto);
        if (!m.find()) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(1)));
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static BigDecimal valor(String texto) {
        String valor = primeiro(VALOR, texto);
        return valor == null ? null : new BigDecimal(valor.replace(".", "").replace(',', '.'));
    }

    private static String numeroRecibo(String texto) {
        Matcher m = NUMERO_RECIBO.matcher(texto);
        if (!m.find()) {
            return null;
        }
        return m.group(1) != null ? m.group(1) : m.group(2);
    }
}
