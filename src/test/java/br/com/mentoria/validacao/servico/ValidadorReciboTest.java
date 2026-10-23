package br.com.mentoria.validacao.servico;

import br.com.mentoria.validacao.dominio.DadosRecibo;
import br.com.mentoria.validacao.dominio.ResultadoValidacao;
import br.com.mentoria.validacao.dominio.StatusValidacao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ValidadorReciboTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 21);

    private final ValidadorRecibo validador = new ValidadorRecibo(12);

    private static DadosRecibo recibo(String crm, LocalDate data, BigDecimal valor) {
        return new DadosRecibo("Maria Souza", "123.456.789-09", "Clinica Exemplo", "11.222.333/0001-81",
                crm, "Consulta medica", data, valor, "42");
    }

    @Test
    void aprovaReciboValido() {
        ResultadoValidacao r = validador.validar(recibo("CRM-PE 12345", HOJE.minusDays(10), new BigDecimal("250.00")), HOJE);

        assertThat(r.aprovado()).isTrue();
        assertThat(r.status()).isEqualTo(StatusValidacao.APROVADO);
        assertThat(r.pendencias()).isEmpty();
    }

    @Test
    void reprovaSemRegistroForaDoPrazoEValorZero() {
        ResultadoValidacao r = validador.validar(recibo(" ", HOJE.minusMonths(13), BigDecimal.ZERO), HOJE);

        assertThat(r.aprovado()).isFalse();
        assertThat(r.status()).isEqualTo(StatusValidacao.REPROVADO);
        assertThat(r.pendencias()).containsExactly(
                "Falhou: Registro profissional encontrado",
                "Falhou: Atendimento dentro de 12 meses",
                "Falhou: Valor maior que zero");
    }

    @Test
    void reprovaSemDataDeAtendimento() {
        ResultadoValidacao r = validador.validar(recibo("CRM-PE 12345", null, new BigDecimal("250.00")), HOJE);

        assertThat(r.pendencias()).containsExactly("Data do atendimento nao reconhecida");
    }
}
