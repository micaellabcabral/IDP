package br.com.mentoria.validacao.persistencia;

import br.com.mentoria.validacao.dominio.StatusValidacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ValidacaoRegistroRepository extends JpaRepository<ValidacaoRegistro, Long> {

    /** Um recibo ja aceito (aprovado ou em revisao) nao pode ser reembolsado de novo. */
    boolean existsByChaveReciboAndStatusNot(String chaveRecibo, StatusValidacao status);

    List<ValidacaoRegistro> findTop20ByOrderByCriadoEmDescIdDesc();
}
