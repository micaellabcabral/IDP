package br.com.mentoria.validacao.dominio;

import java.util.List;

/**
 * O que foi extraido do recibo, a carteirinha informada, as regras atendidas e as etapas do pipeline,
 * gravados cifrados junto com o resultado da validacao. Registros antigos podem nao ter as regras e as etapas.
 */
public record DadosGuardados(
        DadosRecibo recibo,
        String carteirinhaBeneficiario,
        List<String> regrasAtendidas,
        List<EtapaPipeline> etapas) {

    public DadosGuardados {
        regrasAtendidas = regrasAtendidas == null ? List.of() : regrasAtendidas;
        etapas = etapas == null ? List.of() : etapas;
    }
}
