package br.com.mentoria.validacao.dominio;

/** O que foi extraido do recibo e a carteirinha informada, gravados cifrados junto com o resultado da validacao. */
public record DadosGuardados(
        DadosRecibo recibo,
        String carteirinhaBeneficiario) {
}
