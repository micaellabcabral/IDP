package br.com.mentoria.validacao.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DadosRecibo(
        String nomePaciente,
        String cpfPaciente,
        String prestador,
        String documentoPrestador,
        String registroProfissional,
        String servico,
        LocalDate dataAtendimento,
        BigDecimal valor,
        String numeroRecibo) {
}
