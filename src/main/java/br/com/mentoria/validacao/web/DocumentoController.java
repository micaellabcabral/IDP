package br.com.mentoria.validacao.web;

import br.com.mentoria.validacao.servico.ValidacaoDocumentoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/documentos")
public class DocumentoController {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ValidacaoDocumentoService service;

    public DocumentoController(ValidacaoDocumentoService service) {
        this.service = service;
    }

    @GetMapping("/exemplo")
    public ValidacaoRequest exemplo() {
        String conteudo = """
                RECIBO DE CONSULTA MEDICA
                Numero do recibo: 1024
                Paciente: Joao da Silva
                CPF: 529.982.247-25
                Profissional: Dra. Ana Paula Ribeiro
                CRM-SP 123456
                CNPJ: 11.222.333/0001-81
                Servico: Consulta medica
                Data do atendimento: %s
                Valor: R$ 350,00
                """.formatted(LocalDate.now().minusDays(10).format(DATA));
        return new ValidacaoRequest("recibo-consulta.pdf", "application/pdf", conteudo, null, "0007654321");
    }

    @PostMapping("/validar")
    public ValidacaoResponse validar(@RequestBody ValidacaoRequest request) {
        return service.validar(request);
    }
}
