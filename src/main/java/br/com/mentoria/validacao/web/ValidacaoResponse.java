package br.com.mentoria.validacao.web;

import br.com.mentoria.validacao.dominio.DadosRecibo;
import br.com.mentoria.validacao.dominio.EtapaPipeline;
import br.com.mentoria.validacao.dominio.ResultadoValidacao;

import java.util.List;

public record ValidacaoResponse(
        String nomeArquivo,
        List<EtapaPipeline> etapas,
        DadosRecibo dadosExtraidos,
        ResultadoValidacao resultado) {
}
