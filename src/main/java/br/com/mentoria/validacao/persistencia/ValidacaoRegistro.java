package br.com.mentoria.validacao.persistencia;

import br.com.mentoria.validacao.dominio.ResultadoValidacao;
import br.com.mentoria.validacao.dominio.StatusValidacao;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Resultado de uma validacao. Guarda so o necessario para auditoria e para barrar recibo duplicado:
 * nao guarda o texto do documento, a imagem nem os dados pessoais extraidos (nome, CPF, CNPJ, valor).
 */
@Entity
@Table(indexes = @Index(columnList = "chaveRecibo"))
public class ValidacaoRegistro {

    private static final String SEPARADOR = "\n";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nomeArquivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusValidacao status;

    private boolean aprovado;

    @Column(length = 2000)
    private String pendencias;

    @Column(nullable = false)
    private Instant criadoEm;

    /** Hash SHA-256 de numero do recibo + documento do prestador: identifica o recibo sem guardar o documento. */
    @JsonIgnore
    @Column(length = 64)
    private String chaveRecibo;

    protected ValidacaoRegistro() {
    }

    public ValidacaoRegistro(String nomeArquivo, ResultadoValidacao resultado, String chaveRecibo, Instant criadoEm) {
        this.nomeArquivo = nomeArquivo;
        this.status = resultado.status();
        this.aprovado = resultado.aprovado();
        this.pendencias = String.join(SEPARADOR, resultado.pendencias());
        this.chaveRecibo = chaveRecibo;
        this.criadoEm = criadoEm;
    }

    public Long getId() {
        return id;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public StatusValidacao getStatus() {
        return status;
    }

    public boolean isAprovado() {
        return aprovado;
    }

    public List<String> getPendencias() {
        return pendencias == null || pendencias.isEmpty() ? List.of() : Arrays.asList(pendencias.split(SEPARADOR));
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
