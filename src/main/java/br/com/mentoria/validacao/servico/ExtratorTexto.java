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
            Pattern.compile("(?im)^\\s*(?:nome\\s+do\\s+)?paciente[ \\t]*:[ \\t]*(.+?)[ \\t]*$");
    private static final Pattern CPF_PACIENTE =
            Pattern.compile("(?im)^\\s*CPF(?:\\s+do\\s+paciente)?[ \\t]*:[ \\t]*(\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2})\\b");
    private static final Pattern PRESTADOR =
            Pattern.compile("(?im)^\\s*(?:nome\\s+do\\s+)?(?:prestador|profissional)[ \\t]*:[ \\t]*(.+?)[ \\t]*$");
    private static final Pattern DOCUMENTO_PRESTADOR =
            Pattern.compile("(?im)^\\s*(?:CPF/CNPJ|CNPJ|CPF\\s+do\\s+prestador)[^:\\r\\n]*:\\s*"
                    + "(\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}|\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2})\\b");
    private static final Pattern REGISTRO_PROFISSIONAL =
            Pattern.compile("(?i)\\b((?:CRM|CRO)[ \\t/-]*[A-Z]{2}[ \\t/-]*\\d{4,6}|CRP[ \\t/-]*\\d{2}[ \\t/-]*\\d{4,6})\\b");
    private static final Pattern SERVICO =
            Pattern.compile("(?imu)^\\s*servi[cç]o[ \\t]*:[ \\t]*(.+?)[ \\t]*$");
    private static final Pattern DATA_ATENDIMENTO =
            Pattern.compile("(?im)^\\s*data(?:\\s+do\\s+atendimento)?[ \\t]*:[ \\t]*(\\d{2})/(\\d{2})/(\\d{4})\\b");
    private static final Pattern VALOR =
            Pattern.compile("(?i)R\\$\\s*(\\d{1,3}(?:\\.\\d{3})+,\\d{2}|\\d+,\\d{2}|\\d+\\.\\d{2}(?!\\d))");
    private static final Pattern NUMERO_RECIBO =
            Pattern.compile("(?iu)\\brecibo\\s*(?:n(?:\u00ba|\u00b0|o|\\.|\u00famero|umero)?\\s*)?:?\\s*(\\d+)\\b"
                    + "|\\bn(?:\u00fa|u)mero\\s+do\\s+recibo\\s*:?\\s*(\\d+)\\b");

    // Recibo em texto corrido: "Eu, NOME (prestador), CPF ..., recebi de NOME (paciente), CPF ..., a quantia de R$ ...,
    // referente a SERVICO ... Recife, 06 de maio de 2026."
    private static final Pattern PRESTADOR_TEXTO = Pattern.compile("(?iu)\\beu,?\\s+([\\p{L}][\\p{L} .'-]+?)\\s*,");
    private static final Pattern PRESTADOR_DOUTOR = Pattern.compile("(?mu)^[ \\t]*(Dra?\\.[ \\t]+[\\p{L}][\\p{L} .'-]+?)[ \\t]*$");
    private static final Pattern PACIENTE_TEXTO = Pattern.compile(
            "(?iu)\\brecebi(?:\\s+o\\s+valor)?\\s+(?:de|do|da)\\s+(?:sr\\.?\\s+|sra\\.?\\s+)?([\\p{L}][\\p{L} .'-]+?)\\s*(?:,|\\(|\\bCPF\\b|\\binscrit|\\bportador)");
    private static final Pattern RECEBI = Pattern.compile("(?iu)\\brecebi\\b");
    private static final Pattern CPF = Pattern.compile("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b");
    private static final Pattern CPF_OU_CNPJ = Pattern.compile(
            "\\b(\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}|\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2})\\b");
    private static final Pattern SERVICO_TEXTO = Pattern.compile(
            "(?iu)\\breferente\\s+(?:a|ao|aos|\u00e0|\u00e0s)\\s+(.{3,120}?)(?=[.;]|,\\s+realizad|\\s+realizad|\\s+em\\s+\\d{2}/|$)");
    private static final Pattern DATA_REALIZADA = Pattern.compile(
            "(?iu)\\b(?:realizad[oa]s?|atendimento|consulta)\\s+(?:em|no\\s+dia|dia)\\s+(\\d{2})/(\\d{2})/(\\d{4})\\b");
    private static final Pattern DATA_POR_EXTENSO = Pattern.compile(
            "(?iu)\\b(\\d{1,2})\\s+de\\s+(janeiro|fevereiro|mar[c\u00e7]o|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)\\s+de\\s+(\\d{4})\\b");
    private static final Pattern DATA_QUALQUER = Pattern.compile("\\b(\\d{2})/(\\d{2})/(\\d{4})\\b");
    // Boleto: pagador e CPF na mesma linha, beneficiario (prestador) antes do CNPJ e o numero do documento
    // na linha de valores, abaixo ou acima dos rotulos (Data do Documento, Num. Documento...)
    private static final Pattern PAGADOR = Pattern.compile("(?imu)^[ \\t]*(?:pagador|sacado)[ \\t]*:[ \\t]*([\\p{L}][\\p{L} .'-]+?)[ \\t]*$");
    private static final Pattern NOME_E_CPF = Pattern.compile(
            "(?mu)^[ \\t]*([\\p{L}][\\p{L} .'-]{3,}?)[ \\t]*[-\u2013|,]?[ \\t]*(?:CPF(?:[ \\t]*/[ \\t]*CNPJ)?[ \\t]*:?[ \\t]*)?(\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2})\\b");
    private static final Pattern NOME_ANTES_DO_CNPJ = Pattern.compile(
            "(?mu)^[ \\t]*([\\p{L}][\\p{L} .'-]{3,}?)[ \\t]*[-\u2013|,]?[ \\t]*(?:CNPJ[ \\t]*:?[ \\t]*)?\\d{2}\\.?\\d{3}\\.?\\d{3}/\\d{4}-?\\d{2}\\b");
    private static final Pattern NUMERO_DOCUMENTO = Pattern.compile(
            "(?iu)\\bn(?:u|\u00fa)m(?:ero|\\.)?[ \\t]+(?:do[ \\t]+)?documento[ \\t]*:?[ \\t]*(\\d{4,20})\\b");
    private static final Pattern LINHA_DE_VALORES_DO_BOLETO = Pattern.compile(
            "(?m)^[ \\t]*\\d{2}/\\d{2}/\\d{4}[ \\t]+(?:\\d{2}/\\d{2}/\\d{4}[ \\t]+)?(\\d{6,20})\\b");
    private static final Pattern MENCIONA_DOCUMENTO = Pattern.compile("(?iu)\\bdocumento\\b");

    private static final java.util.List<String> MESES = java.util.List.of("janeiro", "fevereiro", "marco", "abril", "maio",
            "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro");

    /**
     * Primeiro procura "Rotulo: valor"; se nao achar, le o recibo em texto corrido: prestador em "Eu, NOME,",
     * paciente em "recebi de NOME", o CPF/CNPJ antes de "recebi" e do prestador e o CPF depois e do paciente,
     * servico em "referente a ..." e a data do atendimento (ou a data do recibo, por extenso).
     */
    public DadosRecibo extrair(String texto) {
        String conteudo = texto == null ? "" : texto;
        Matcher recebi = RECEBI.matcher(conteudo);
        String antes = recebi.find() ? conteudo.substring(0, recebi.start()) : null;
        String depois = antes == null ? null : conteudo.substring(recebi.start());
        Matcher nomeECpf = NOME_E_CPF.matcher(conteudo);
        boolean temNomeECpf = nomeECpf.find();
        return new DadosRecibo(
                ou(primeiro(PACIENTE, conteudo), primeiro(PACIENTE_TEXTO, conteudo), primeiro(PAGADOR, conteudo),
                        temNomeECpf ? nomeECpf.group(1) : null),
                ou(primeiro(CPF_PACIENTE, conteudo), depois == null ? null : grupo0(CPF, depois),
                        temNomeECpf ? nomeECpf.group(2) : null),
                ou(primeiro(PRESTADOR, conteudo), primeiro(PRESTADOR_TEXTO, conteudo), primeiro(PRESTADOR_DOUTOR, conteudo),
                        primeiro(NOME_ANTES_DO_CNPJ, conteudo)),
                ou(primeiro(DOCUMENTO_PRESTADOR, conteudo), antes == null ? null : primeiro(CPF_OU_CNPJ, antes)),
                primeiro(REGISTRO_PROFISSIONAL, conteudo),
                ou(primeiro(SERVICO, conteudo), primeiro(SERVICO_TEXTO, conteudo)),
                data(conteudo),
                valor(conteudo),
                ou(numeroRecibo(conteudo), primeiro(NUMERO_DOCUMENTO, conteudo),
                        MENCIONA_DOCUMENTO.matcher(conteudo).find() ? primeiro(LINHA_DE_VALORES_DO_BOLETO, conteudo) : null));
    }

    private static String ou(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank()) {
                return v.strip();
            }
        }
        return null;
    }

    private static String grupo0(Pattern padrao, String texto) {
        Matcher m = padrao.matcher(texto);
        return m.find() ? m.group() : null;
    }

    private static String primeiro(Pattern padrao, String texto) {
        Matcher m = padrao.matcher(texto);
        return m.find() ? m.group(1) : null;
    }

    /** Rotulo "Data do atendimento", depois "realizada em", depois a data do recibo por extenso, depois a primeira data. */
    private static LocalDate data(String texto) {
        for (Pattern p : new Pattern[]{DATA_ATENDIMENTO, DATA_REALIZADA}) {
            Matcher m = p.matcher(texto);
            if (m.find()) {
                LocalDate d = data(m.group(1), m.group(2), m.group(3));
                if (d != null) {
                    return d;
                }
            }
        }
        Matcher extenso = DATA_POR_EXTENSO.matcher(texto);
        if (extenso.find()) {
            String mes = java.text.Normalizer.normalize(extenso.group(2).toLowerCase(), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "");
            LocalDate d = data(extenso.group(1), String.valueOf(MESES.indexOf(mes) + 1), extenso.group(3));
            if (d != null) {
                return d;
            }
        }
        Matcher qualquer = DATA_QUALQUER.matcher(texto);
        return qualquer.find() ? data(qualquer.group(1), qualquer.group(2), qualquer.group(3)) : null;
    }

    private static LocalDate data(String dia, String mes, String ano) {
        try {
            return LocalDate.of(Integer.parseInt(ano), Integer.parseInt(mes), Integer.parseInt(dia));
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal valor(String texto) {
        String valor = primeiro(VALOR, texto);
        if (valor == null) {
            return null;
        }
        // "1.250,00" (padrao brasileiro) ou "53.01" (ponto decimal, comum em cupom fiscal)
        return new BigDecimal(valor.contains(",") ? valor.replace(".", "").replace(',', '.') : valor);
    }

    private static String numeroRecibo(String texto) {
        Matcher m = NUMERO_RECIBO.matcher(texto);
        if (!m.find()) {
            return null;
        }
        return m.group(1) != null ? m.group(1) : m.group(2);
    }
}
