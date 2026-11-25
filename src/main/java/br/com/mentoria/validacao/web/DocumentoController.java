package br.com.mentoria.validacao.web;

import br.com.mentoria.validacao.persistencia.ValidacaoRegistro;
import br.com.mentoria.validacao.servico.ValidacaoDocumentoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/documentos")
public class DocumentoController {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ValidacaoDocumentoService service;

    public DocumentoController(ValidacaoDocumentoService service) {
        this.service = service;
    }

    /** Recibo ficticio com atendimento de 10 dias atras, para o exemplo nunca vencer o prazo. */
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
    public ResponseEntity<ValidacaoResponse> validar(@Valid @RequestBody ValidacaoRequest request) {
        ValidacaoResponse resposta = service.validar(request);
        return resposta.id() == null ? ResponseEntity.ok(resposta)
                : ResponseEntity.ok().location(URI.create("/api/documentos/" + resposta.id())).body(resposta);
    }

    /** Historico: as 20 validacoes mais recentes (sem texto do documento nem dados pessoais). */
    @GetMapping
    public List<ValidacaoRegistro> listar() {
        return service.listarRecentes();
    }

    /** Consulta o resultado salvo de uma validacao (sem texto do documento nem dados pessoais). */
    @GetMapping("/{id}")
    public ResponseEntity<ValidacaoRegistro> buscar(@PathVariable Long id) {
        return ResponseEntity.of(service.buscar(id));
    }

    /** O analista aprova ou reprova um recibo em revisao manual (409 se ele nao estiver em revisao). */
    @PostMapping("/{id}/revisao")
    public ResponseEntity<ValidacaoRegistro> revisar(@PathVariable Long id, @Valid @RequestBody RevisaoRequest revisao) {
        return ResponseEntity.of(service.revisar(id, revisao));
    }
}
