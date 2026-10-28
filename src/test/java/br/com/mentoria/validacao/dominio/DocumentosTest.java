package br.com.mentoria.validacao.dominio;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentosTest {

    @ParameterizedTest
    @ValueSource(strings = {"123.456.789-09", "52998224725"})
    void aceitaCpfValido(String cpf) {
        assertThat(Cpf.valido(cpf)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"123.456.789-00", "111.111.111-11", "1234567890", ""})
    void rejeitaCpfInvalido(String cpf) {
        assertThat(Cpf.valido(cpf)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"11.222.333/0001-81", "11222333000181"})
    void aceitaCnpjValido(String cnpj) {
        assertThat(Cnpj.valido(cnpj)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"11.222.333/0001-80", "00.000.000/0000-00", "1122233300018", ""})
    void rejeitaCnpjInvalido(String cnpj) {
        assertThat(Cnpj.valido(cnpj)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CRM-PE 12345", "crm/sp 123456", "CRO-RJ 1234", "CRP 06/12345", "CRP-0212345"})
    void aceitaRegistroProfissionalValido(String registro) {
        assertThat(RegistroProfissional.valido(registro)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"CRM 12345", "CRM-PE 123", "CRN-PE 12345", "CRP-AB 12345", ""})
    void rejeitaRegistroProfissionalInvalido(String registro) {
        assertThat(RegistroProfissional.valido(registro)).isFalse();
    }
}
