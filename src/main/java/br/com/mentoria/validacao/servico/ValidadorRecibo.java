package br.com.mentoria.validacao.servico;

import br.com.mentoria.validacao.dominio.Cnpj;
import br.com.mentoria.validacao.dominio.Cpf;
import br.com.mentoria.validacao.dominio.DadosRecibo;
import br.com.mentoria.validacao.dominio.ResultadoValidacao;
import br.com.mentoria.validacao.dominio.StatusValidacao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ValidadorRecibo {

    private final int prazoAtendimentoMeses;

    public ValidadorRecibo(@Value("${app.validacao.prazo-atendimento-meses:12}") int prazoAtendimentoMeses) {
        this.prazoAtendimentoMeses = prazoAtendimentoMeses;
    }

    public ResultadoValidacao validar(DadosRecibo dados, LocalDate hoje) {
        List<String> atendidas = new ArrayList<>();
        List<String> pendencias = new ArrayList<>();

        checar(preenchido(dados.nomePaciente()), "Paciente identificado", atendidas, pendencias);
        checar(preenchido(dados.cpfPaciente()), "CPF do paciente encontrado", atendidas, pendencias);
        if (preenchido(dados.cpfPaciente())) {
            checar(Cpf.valido(dados.cpfPaciente()), "CPF do paciente valido", atendidas, pendencias);
        }
        checar(preenchido(dados.documentoPrestador()), "CPF/CNPJ do prestador encontrado", atendidas, pendencias);
        if (preenchido(dados.documentoPrestador())) {
            String doc = dados.documentoPrestador();
            checar(Cpf.valido(doc) || Cnpj.valido(doc), "CPF/CNPJ do prestador valido", atendidas, pendencias);
        }
        checar(preenchido(dados.registroProfissional()), "Registro profissional encontrado", atendidas, pendencias);

        LocalDate atendimento = dados.dataAtendimento();
        if (atendimento == null) {
            pendencias.add("Data do atendimento nao reconhecida");
        } else {
            checar(!atendimento.isAfter(hoje), "Data do atendimento nao esta no futuro", atendidas, pendencias);
            checar(!atendimento.isBefore(hoje.minusMonths(prazoAtendimentoMeses)),
                    "Atendimento dentro de " + prazoAtendimentoMeses + " meses", atendidas, pendencias);
        }

        checar(dados.valor() != null && dados.valor().compareTo(BigDecimal.ZERO) > 0,
                "Valor maior que zero", atendidas, pendencias);

        boolean aprovado = pendencias.isEmpty();
        return new ResultadoValidacao(aprovado, aprovado ? StatusValidacao.APROVADO : StatusValidacao.REPROVADO,
                atendidas, pendencias);
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static void checar(boolean ok, String regra, List<String> atendidas, List<String> pendencias) {
        (ok ? atendidas : pendencias).add(ok ? regra : "Falhou: " + regra);
    }
}
