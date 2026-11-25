package br.com.mentoria.validacao.dominio;

import java.util.regex.Pattern;

public final class Cnpj {

    private static final int[] PESOS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private static final Pattern NO_TEXTO = Pattern.compile("\\b\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}\\b");

    private Cnpj() {
    }

    /** Troca cada CNPJ do texto por asteriscos antes de o texto sair da aplicacao (por exemplo, para a IA). */
    public static String mascarar(String texto) {
        return texto == null ? null : NO_TEXTO.matcher(texto).replaceAll("**.***.***/****-**");
    }

    public static boolean valido(String cnpj) {
        if (cnpj == null) {
            return false;
        }
        String d = cnpj.replaceAll("\\D", "");
        if (d.length() != 14 || d.chars().distinct().count() == 1) {
            return false;
        }
        return digito(d, 12) == d.charAt(12) - '0' && digito(d, 13) == d.charAt(13) - '0';
    }

    private static int digito(String d, int tamanho) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += (d.charAt(i) - '0') * PESOS[PESOS.length - tamanho + i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
