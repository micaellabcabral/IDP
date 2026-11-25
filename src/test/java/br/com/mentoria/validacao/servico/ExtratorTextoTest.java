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

    /** Recibo real em texto corrido, sem rotulos "Campo: valor". */
    @Test
    void extraiReciboEmTextoCorrido() {
        String texto = """
                RECIBO
                Eu, Dra. Ana Paula Ribeiro, medica inscrita no CRM-PE 12345 e no CPF sob o no 111.444.777-35,
                recebi de JOAO DA SILVA, inscrito no CPF sob o no 529.982.247-25, a quantia de R$350,00
                (trezentos e cinquenta reais), referente a consulta medica realizada em 10/11/2026.
                Por ser verdade, firmo o presente recibo.
                Recife, 12 de novembro de 2026.
                """;

        DadosRecibo dados = extrator.extrair(texto);

        assertThat(dados.prestador()).isEqualTo("Dra. Ana Paula Ribeiro");
        assertThat(dados.documentoPrestador()).isEqualTo("111.444.777-35");
        assertThat(dados.registroProfissional()).isEqualTo("CRM-PE 12345");
        assertThat(dados.nomePaciente()).isEqualTo("JOAO DA SILVA");
        assertThat(dados.cpfPaciente()).isEqualTo("529.982.247-25");
        assertThat(dados.servico()).isEqualTo("consulta medica");
        assertThat(dados.dataAtendimento()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(dados.valor()).isEqualByComparingTo(new BigDecimal("350.00"));
    }

    @Test
    void usaADataPorExtensoEValorComPontoDecimal() {
        String texto = "Recebi de Maria Souza, CPF 529.982.247-25, o valor de R$ 53.01 referente a sessao de psicoterapia."
                + "\nOlinda, 3 de março de 2026.";

        DadosRecibo dados = extrator.extrair(texto);

        assertThat(dados.nomePaciente()).isEqualTo("Maria Souza");
        assertThat(dados.dataAtendimento()).isEqualTo(LocalDate.of(2026, 3, 3));
        assertThat(dados.valor()).isEqualByComparingTo(new BigDecimal("53.01"));
        assertThat(dados.servico()).isEqualTo("sessao de psicoterapia");
    }

    @Test
    void rotuloVazioNaoPegaALinhaDeBaixo() {
        DadosRecibo dados = extrator.extrair("Paciente:\nCPF: 529.982.247-25");

        assertThat(dados.nomePaciente()).isNull();
        assertThat(dados.cpfPaciente()).isEqualTo("529.982.247-25");
    }

    /** Layout de boleto bancario: rotulos numa linha e valores em outra, pagador e CPF na mesma linha. */
    @Test
    void extraiPagadorENumeroDoDocumentoDeBoleto() {
        String texto = """
                Pagador CPF/CNPJ
                CLINICA BOA SAUDE LTDA 11.222.333/0001-81
                RUA DAS FLORES, 150 - BOA VIAGEM/RECIFE PE 51021-360
                JOAO DA SILVA 529.982.247-25
                R$ 90,00
                30/11/2025 01/12/2025 00125984581 SIM RG DM
                Data Documento Dt. de Processamento Num. Documento Carteira Especie Aceite
                Pagador:
                CPF/CNPJ: 11.222.333/0001-81
                """;

        DadosRecibo dados = extrator.extrair(texto);

        assertThat(dados.nomePaciente()).isEqualTo("JOAO DA SILVA");
        assertThat(dados.cpfPaciente()).isEqualTo("529.982.247-25");
        assertThat(dados.prestador()).isEqualTo("CLINICA BOA SAUDE LTDA");
        assertThat(dados.documentoPrestador()).isEqualTo("11.222.333/0001-81");
        assertThat(dados.numeroRecibo()).isEqualTo("00125984581");
        assertThat(dados.dataAtendimento()).isEqualTo(LocalDate.of(2025, 11, 30));
        assertThat(dados.valor()).isEqualByComparingTo(new BigDecimal("90.00"));
    }

    @Test
    void numeroDoDocumentoComRotuloNaMesmaLinha() {
        assertThat(extrator.extrair("Num. Documento: 4471\nPagador: Maria Souza").numeroRecibo()).isEqualTo("4471");
        assertThat(extrator.extrair("Pagador: Maria Souza").nomePaciente()).isEqualTo("Maria Souza");
    }
}
