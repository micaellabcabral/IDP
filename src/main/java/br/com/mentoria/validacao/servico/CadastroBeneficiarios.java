package br.com.mentoria.validacao.servico;

import br.com.mentoria.validacao.dominio.Beneficiario;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class CadastroBeneficiarios {

    private final Map<String, Beneficiario> porCarteirinha = Map.of(
            "0001234567", new Beneficiario("0001234567", "Maria Souza", "123.456.789-09"),
            "0007654321", new Beneficiario("0007654321", "Joao da Silva", "529.982.247-25"));

    public Optional<Beneficiario> buscarPorCarteirinha(String carteirinha) {
        if (carteirinha == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(porCarteirinha.get(carteirinha.trim()));
    }
}
