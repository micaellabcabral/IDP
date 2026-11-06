package br.com.mentoria.validacao.dominio;

import java.util.regex.Pattern;

public final class RegistroProfissional {

    private static final Pattern CRM_CRO = Pattern.compile("^(CRM|CRO)[\\s/-]*[A-Z]{2}[\\s/-]*\\d{4,6}$");
    private static final Pattern CRP = Pattern.compile("^CRP[\\s/-]*\\d{2}[\\s/-]*\\d{4,6}$");

    private RegistroProfissional() {
    }

    public static boolean valido(String registro) {
        if (registro == null) {
            return false;
        }
        String r = registro.trim().toUpperCase();
        return CRM_CRO.matcher(r).matches() || CRP.matcher(r).matches();
    }
}
