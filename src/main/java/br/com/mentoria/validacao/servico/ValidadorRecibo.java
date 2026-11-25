package br.com.mentoria.validacao.servico;

import br.com.mentoria.validacao.dominio.Beneficiario;
import br.com.mentoria.validacao.dominio.Cnpj;
import br.com.mentoria.validacao.dominio.Cpf;
import br.com.mentoria.validacao.dominio.DadosRecibo;
import br.com.mentoria.validacao.dominio.RegistroProfissional;
import br.com.mentoria.validacao.dominio.ResultadoValidacao;
import br.com.mentoria.validacao.dominio.StatusValidacao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class ValidadorRecibo {

    /** Pendencias de campo nao encontrado: indicam leitura incerta (OCR ruim ou valor da IA descartado). */
    private static final Set<String> CAMPO_AUSENTE = Set.of(
            "Falhou: Paciente identificado",
            "Falhou: CPF do paciente encontrado",
            "Falhou: CPF/CNPJ do prestador encontrado",
            "Falhou: Registro profissional encontrado",
            "Data do atendimento nao reconhecida");

    private final int prazoAtendimentoMeses;
    private final BigDecimal limiteReembolso;

    public ValidadorRecibo(int prazoAtendimentoMeses) {
        this(prazoAtendimentoMeses, new BigDecimal("1000.00"));
    }

    @Autowired
    public ValidadorRecibo(@Value("${app.validacao.prazo-atendimento-meses:12}") int prazoAtendimentoMeses,
                           @Value("${app.validacao.limite-reembolso:1000.00}") BigDecimal limiteReembolso) {
        this.prazoAtendimentoMeses = prazoAtendimentoMeses;
        this.limiteReembolso = limiteReembolso;
    }

    public ResultadoValidacao validar(DadosRecibo dados, LocalDate hoje) {
        return validar(dados, hoje, null);
    }

    public ResultadoValidacao validar(DadosRecibo dados, LocalDate hoje, Beneficiario beneficiario) {
        List<String> atendidas = new ArrayList<>();
        List<String> pendencias = new ArrayList<>();

        if (beneficiario != null) {
            checar(mesmoNome(dados.nomePaciente(), beneficiario.nome())
                            && mesmosDigitos(dados.cpfPaciente(), beneficiario.cpf()),
                    "Paciente confere com o beneficiario", atendidas, pendencias);
        }

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
        if (preenchido(dados.registroProfissional())) {
            checar(RegistroProfissional.valido(dados.registroProfissional()),
                    "Registro profissional em formato valido (CRM, CRP ou CRO)", atendidas, pendencias);
        }

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

        StatusValidacao status;
        if (pendencias.isEmpty()) {
            // recibo correto, mas acima do limite do plano: um analista confere antes de reembolsar
            boolean acimaDoLimite = dados.valor().compareTo(limiteReembolso) > 0;
            if (acimaDoLimite) {
                pendencias.add("Valor acima do limite de reembolso do plano (R$ " + limiteReembolso + "): revisao manual");
            }
            status = acimaDoLimite ? StatusValidacao.REVISAO_MANUAL : StatusValidacao.APROVADO;
        } else {
            // so faltam campos e algo foi lido: extracao incerta, vai para revisao em vez de reprovar direto
            boolean soCamposAusentes = CAMPO_AUSENTE.containsAll(pendencias);
            status = soCamposAusentes && !atendidas.isEmpty() ? StatusValidacao.REVISAO_MANUAL : StatusValidacao.REPROVADO;
        }
        return new ResultadoValidacao(status == StatusValidacao.APROVADO, status, atendidas, pendencias);
    }

    private static boolean mesmoNome(String a, String b) {
        return preenchido(a) && preenchido(b) && normalizar(a).equals(normalizar(b));
    }

    private static String normalizar(String nome) {
        String semAcento = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.trim().replaceAll("\\s+", " ").toUpperCase();
    }

    private static boolean mesmosDigitos(String a, String b) {
        return preenchido(a) && preenchido(b) && a.replaceAll("\\D", "").equals(b.replaceAll("\\D", ""));
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static void checar(boolean ok, String regra, List<String> atendidas, List<String> pendencias) {
        (ok ? atendidas : pendencias).add(ok ? regra : "Falhou: " + regra);
    }
}
