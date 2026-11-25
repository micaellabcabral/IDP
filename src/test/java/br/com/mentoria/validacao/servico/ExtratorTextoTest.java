package br.com.mentoria.validacao.servico;

import br.com.mentoria.validacao.dominio.DadosRecibo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ExtratorTextoTest {

    private final ExtratorTexto extrator = new ExtratorTexto();

    @Test
    void extraiTodosOsCamposDoRecibo() {
        String texto = """
                RECIBO DE CONSULTA MEDICA
                Recibo nº: 4521
                Paciente: Maria Oliveira Santos
                CPF: 529.982.247-25
                Profissional: Dra. Ana Paula Ribeiro
                CRM-PE 12345
                CNPJ: 11.222.333/0001-81
                Data do atendimento: 15/09/2026
                Valor: R$ 1.250,00
                """;

        DadosRecibo dados = extrator.extrair(texto);

        assertThat(dados.nomePaciente()).isEqualTo("Maria Oliveira Santos");
        assertThat(dados.cpfPaciente()).isEqualTo("529.982.247-25");
        assertThat(dados.prestador()).isEqualTo("Dra. Ana Paula Ribeiro");
        assertThat(dados.registroProfissional()).isEqualTo("CRM-PE 12345");
        assertThat(dados.documentoPrestador()).isEqualTo("11.222.333/0001-81");
        assertThat(dados.dataAtendimento()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(dados.valor()).isEqualByComparingTo(new BigDecimal("1250.00"));
        assertThat(dados.numeroRecibo()).isEqualTo("4521");
    }

    @Test
    void extraiVariacoesDeRotulo() {
        String texto = """
                Numero do recibo: 77
                Nome do paciente: Joao Pereira
                CPF do paciente: 52998224725
                Prestador: Clinica Vida
                CPF do prestador: 123.456.789-09
                Registro: CRP 06/12345
                Data: 01/10/2026
                Total R$ 250,50
                """;

        DadosRecibo dados = extrator.extrair(texto);

        assertThat(dados.numeroRecibo()).isEqualTo("77");
        assertThat(dados.nomePaciente()).isEqualTo("Joao Pereira");
        assertThat(dados.cpfPaciente()).isEqualTo("52998224725");
        assertThat(dados.prestador()).isEqualTo("Clinica Vida");
        assertThat(dados.documentoPrestador()).isEqualTo("123.456.789-09");
        assertThat(dados.registroProfissional()).isEqualTo("CRP 06/12345");
        assertThat(dados.dataAtendimento()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(dados.valor()).isEqualByComparingTo(new BigDecimal("250.50"));
    }

    @Test
    void ignoraDataInexistente() {
        DadosRecibo dados = extrator.extrair("Data do atendimento: 31/02/2026");

        assertThat(dados.dataAtendimento()).isNull();
    }

    @Test
    void retornaCamposNulosQuandoTextoVazioOuNulo() {
        DadosRecibo vazio = new DadosRecibo(null, null, null, null, null, null, null, null, null);

        assertThat(extrator.extrair(null)).isEqualTo(vazio);
        assertThat(extrator.extrair("documento sem dados")).isEqualTo(vazio);
    }
}
