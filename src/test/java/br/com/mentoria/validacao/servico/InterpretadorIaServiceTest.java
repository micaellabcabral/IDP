package br.com.mentoria.validacao.servico;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** Nao chama a OpenAI: cobre o mascaramento, a conferencia anti-alucinacao e o fallback sem chave. */
class InterpretadorIaServiceTest {

    private static final String RECIBO = """
            RECIBO DE CONSULTA MEDICA
            Numero do recibo: 1024
            Paciente: João da Silva
            CPF: 529.982.247-25
            Profissional: Dra. Ana Paula Ribeiro
            CRM-SP 123456
            CNPJ: 11.222.333/0001-81
            Servico: Consulta medica
            Data do atendimento: 10/11/2026
            Valor: R$ 350,00
            """;

    @Test
    void mascaraCpfECnpjAntesDeEnviarParaAIa() {
        String mascarado = InterpretadorIaService.mascarar(RECIBO + "CPF sem pontos 52998224725 e CNPJ 11222333000181");

        assertThat(mascarado)
                .doesNotContain("529.982.247-25", "52998224725", "11.222.333/0001-81", "11222333000181")
                .contains("***.***.***-**", "**.***.***/****-**", "Paciente: João da Silva", "R$ 350,00");
    }

    @Test
    void aceitaSoValoresQueAparecemNoTexto() {
        assertThat(InterpretadorIaService.noTexto("JOAO DA SILVA", RECIBO)).isEqualTo("JOAO DA SILVA");
        assertThat(InterpretadorIaService.noTexto("Dra. Ana Paula Ribeiro", RECIBO)).isEqualTo("Dra. Ana Paula Ribeiro");
        assertThat(InterpretadorIaService.noTexto("Carlos Inventado", RECIBO)).isNull();
        assertThat(InterpretadorIaService.noTexto(null, RECIBO)).isNull();
    }

    @Test
    void semChaveExtraiTudoLocalmente() throws IOException {
        var ia = new InterpretadorIaService("", new ExtratorTexto());

        var dados = ia.interpretar(RECIBO);

        assertThat(ia.configurado()).isFalse();
        assertThat(dados.nomePaciente()).isEqualTo("João da Silva");
        assertThat(dados.servico()).isEqualTo("Consulta medica");
        assertThat(dados.numeroRecibo()).isEqualTo("1024");
    }
}
