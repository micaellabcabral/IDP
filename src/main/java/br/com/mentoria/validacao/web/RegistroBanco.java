package br.com.mentoria.validacao.web;

import br.com.mentoria.validacao.dominio.DadosRecibo;
import br.com.mentoria.validacao.dominio.StatusValidacao;

import java.time.Instant;
import java.util.List;

/** Linha da tela Banco de Dados e do JSON de exportacao: resultado da validacao mais os dados do recibo. */
public record RegistroBanco(
        Long id,
        String nomeArquivo,
        StatusValidacao status,
        boolean aprovado,
        List<String> pendencias,
        Instant criadoEm,
        boolean revisadoManualmente,
        String motivoRevisao,
        Instant revisadoEm,
        String carteirinhaBeneficiario,
        DadosRecibo recibo) {
}
