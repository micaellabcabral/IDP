package br.com.mentoria.validacao.servico;

import br.com.mentoria.validacao.dominio.DadosRecibo;
import br.com.mentoria.validacao.dominio.ResultadoValidacao;
import br.com.mentoria.validacao.dominio.StatusValidacao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Revisao manual: leitura incerta ou valor acima do limite do plano vao para um analista, em vez de aprovar ou reprovar. */
class ConfiabilidadeTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 24);
    private final ValidadorRecibo validador = new ValidadorRecibo(12, new BigDecimal("1000.00"));

    private static DadosRecibo recibo(String paciente, String registro, BigDecimal valor) {
        return new DadosRecibo(paciente, "123.456.789-09", "Clinica Exemplo", "11.222.333/0001-81",
                registro, "Consulta medica", HOJE.minusDays(5), valor, "42");
    }

    @Test
    void valorAcimaDoLimiteVaiParaRevisaoManual() {
        ResultadoValidacao r = validador.validar(recibo("Maria Souza", "CRM-PE 12345", new BigDecimal("1500.00")), HOJE);

        assertThat(r.status()).isEqualTo(StatusValidacao.REVISAO_MANUAL);
        assertThat(r.aprovado()).isFalse();
        assertThat(r.pendencias()).containsExactly(
                "Valor acima do limite de reembolso do plano (R$ 1000.00): revisao manual");
    }

    @Test
    void valorNoLimiteEAprovado() {
        assertThat(validador.validar(recibo("Maria Souza", "CRM-PE 12345", new BigDecimal("1000.00")), HOJE).status())
                .isEqualTo(StatusValidacao.APROVADO);
    }

    @Test
    void campoNaoLidoVaiParaRevisaoManual() {
        ResultadoValidacao r = validador.validar(recibo(null, "CRM-PE 12345", new BigDecimal("250.00")), HOJE);

        assertThat(r.status()).isEqualTo(StatusValidacao.REVISAO_MANUAL);
        assertThat(r.pendencias()).containsExactly("Falhou: Paciente identificado");
    }

    @Test
    void dadoInvalidoReprovaMesmoComCampoFaltando() {
        ResultadoValidacao r = validador.validar(recibo(null, "CRM 12345", new BigDecimal("250.00")), HOJE);

        assertThat(r.status()).isEqualTo(StatusValidacao.REPROVADO);
    }

    @Test
    void textoSemNenhumDadoReprova() {
        var vazio = new DadosRecibo(null, null, null, null, null, null, null, null, null);

        assertThat(validador.validar(vazio, HOJE).status()).isEqualTo(StatusValidacao.REPROVADO);
    }
}
