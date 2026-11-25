package br.com.mentoria.validacao.web;

public record ValidacaoRequest(
        String nomeArquivo,
        String mimeType,
        String conteudoDocumento,
        String imagemBase64,
        String carteirinhaBeneficiario) {
}
