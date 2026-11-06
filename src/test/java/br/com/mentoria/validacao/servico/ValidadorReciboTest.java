package br.com.mentoria.validacao.servico;

import br.com.mentoria.validacao.dominio.Beneficiario;
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

    @Test
    void reprovaCpfCnpjERegistroInvalidos() {
        DadosRecibo dados = new DadosRecibo("Maria Souza", "123.456.789-00", "Clinica Exemplo", "11.222.333/0001-80",
                "CRM 12345", "Consulta medica", HOJE.minusDays(10), new BigDecimal("250.00"), "42");

        ResultadoValidacao r = validador.validar(dados, HOJE);

        assertThat(r.status()).isEqualTo(StatusValidacao.REPROVADO);
        assertThat(r.pendencias()).containsExactly(
                "Falhou: CPF do paciente valido",
                "Falhou: CPF/CNPJ do prestador valido",
                "Falhou: Registro profissional em formato valido (CRM, CRP ou CRO)");
    }

    @Test
    void aprovaQuandoPacienteConfereComBeneficiario() {
        Beneficiario beneficiario = new CadastroBeneficiarios().buscarPorCarteirinha("0001234567").orElseThrow();

        ResultadoValidacao r = validador.validar(recibo("CRM-PE 12345", HOJE.minusDays(10), new BigDecimal("250.00")),
                HOJE, beneficiario);

        assertThat(r.aprovado()).isTrue();
        assertThat(r.regrasAtendidas()).contains("Paciente confere com o beneficiario");
    }

    @Test
    void reprovaPacienteDiferenteDoBeneficiario() {
        Beneficiario beneficiario = new CadastroBeneficiarios().buscarPorCarteirinha("0007654321").orElseThrow();

        ResultadoValidacao r = validador.validar(recibo("CRM-PE 12345", HOJE.minusDays(10), new BigDecimal("250.00")),
                HOJE, beneficiario);

        assertThat(r.status()).isEqualTo(StatusValidacao.REPROVADO);
        assertThat(r.pendencias()).containsExactly("Falhou: Paciente confere com o beneficiario");
    }

    @Test
    void naoEncontraCarteirinhaInexistente() {
        assertThat(new CadastroBeneficiarios().buscarPorCarteirinha("9999999999")).isEmpty();
    }
}
