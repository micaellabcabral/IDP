# IDP — Validação Inteligente de Recibos Médicos

<!-- badges -->
![Java](https://img.shields.io/badge/Java-ED8B00?style=flat-square&logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=flat-square&logo=springboot&logoColor=white) ![Maven](https://img.shields.io/badge/Maven-C71A36?style=flat-square&logo=apachemaven&logoColor=white) ![JUnit 5](https://img.shields.io/badge/JUnit%205-25A162?style=flat-square&logo=junit5&logoColor=white) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=flat-square&logo=postgresql&logoColor=white) ![HTML5](https://img.shields.io/badge/HTML5-E34F26?style=flat-square&logo=html5&logoColor=white) ![CSS](https://img.shields.io/badge/CSS-663399?style=flat-square&logo=css&logoColor=white) ![JavaScript](https://img.shields.io/badge/JavaScript-F7DF1E?style=flat-square&logo=javascript&logoColor=black) ![Python](https://img.shields.io/badge/Python-3776AB?style=flat-square&logo=python&logoColor=white)

[Read in English](README.en.md)

Sistema em Java e Spring Boot, com front-end web, para validar recibos e notas fiscais de consultas médicas em pedidos de reembolso de plano de saúde. Recebe um PDF ou imagem, extrai o texto, estrutura os dados, confere com o beneficiário do plano e aprova, reprova ou encaminha para revisão manual. Projeto da Mentoria Avanade 2026.2.

## Acesso rápido

Com o sistema rodando (`./mvnw spring-boot:run`):

| Tela | Link |
| --- | --- |
| Validação de recibos (tela principal) | [http://localhost:8080](http://localhost:8080) |
| Banco de Dados | [http://localhost:8080/banco.html](http://localhost:8080/banco.html) |
| Exportar o banco em JSON | [http://localhost:8080/api/banco/exportar](http://localhost:8080/api/banco/exportar) |
| Recibo de exemplo da API | [http://localhost:8080/api/documentos/exemplo](http://localhost:8080/api/documentos/exemplo) |

Se a porta 8080 estiver ocupada, suba em outra e troque o número nos links: `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8765` (então [http://localhost:8765](http://localhost:8765) e [http://localhost:8765/banco.html](http://localhost:8765/banco.html)).

## Banco de Dados

A tela `banco.html` (menu **Banco de Dados**) lista todos os recibos validados: número, arquivo, status, paciente, prestador, valor, data do atendimento, pendências e data da validação. Cada linha abre todos os dados do recibo, a carteirinha e a revisão manual. Dá para filtrar por status, buscar por paciente, prestador, CPF ou arquivo e **baixar em JSON** todos os registros (ou um só), para migrar para o banco de outro sistema.

No perfil dev o H2 é gravado em `data/idp.mv.db`, então os registros continuam lá depois de reiniciar. Para zerar, apague a pasta `data/`.

## Fluxo

```
Recibo (PDF/imagem)
   → PDF com texto: lido direto pelo PDFBox, sem OCR (PDF escaneado segue como imagem)
   → OpenCV: pré-processamento da imagem          (OpenCvService, comando externo)
   → Tesseract: extração do texto                 (OcrService, comando externo)
   → IA: texto em dados estruturados              (InterpretadorIaService, OpenAI)
   → Regras de negócio                            (ValidadorRecibo + cadastro de beneficiários)
   → APROVADO | REPROVADO | REVISAO_MANUAL
```

Cada etapa tem fallback local, então o sistema funciona sem chave de IA, sem Tesseract e sem OpenCV instalados:

| Etapa | Com configuração | Sem configuração ou em caso de falha |
| --- | --- | --- |
| OpenCV | `app.opencv.command` trata a imagem | segue com a imagem original |
| Tesseract | `app.ocr.tessdata-path` extrai o texto da imagem | usa o texto de `conteudoDocumento` |
| IA | `OPENAI_API_KEY` interpreta o texto | extração local por regex |

A resposta mostra, em `etapas`, qual caminho foi usado em cada etapa.

## Endpoints

| Método | Rota | Descrição |
| --- | --- | --- |
| `GET` | `/` | Front-end: validar um recibo e ver o histórico |
| `GET` | `/api/documentos/exemplo` | Recibo de exemplo (atendimento sempre de 10 dias atrás) |
| `POST` | `/api/documentos/validar` | Executa o pipeline e devolve etapas, dados extraídos e resultado |
| `GET` | `/api/documentos` | Histórico: as 20 validações mais recentes |
| `GET` | `/api/documentos/{id}` | Consulta o resultado salvo de uma validação |
| `POST` | `/api/documentos/{id}/revisao` | Analista aprova ou reprova um recibo em revisão manual (`decisao` e `motivo`) |
| `GET` | `/api/banco` | Registros com os dados dos recibos (tela Banco de Dados) |
| `GET` | `/api/banco/exportar` | Download do JSON com todos os registros, para migração |

Request de `POST /api/documentos/validar` (envie `conteudoDocumento`, `imagemBase64` ou ambos; `carteirinhaBeneficiario` é opcional):

```json
{
  "nomeArquivo": "recibo-consulta.pdf",
  "mimeType": "application/pdf",
  "conteudoDocumento": "RECIBO DE CONSULTA MEDICA\nNumero do recibo: 1024\nPaciente: Joao da Silva\nCPF: 529.982.247-25\nProfissional: Dra. Ana Paula Ribeiro\nCRM-SP 123456\nCNPJ: 11.222.333/0001-81\nServico: Consulta medica\nData do atendimento: 14/11/2026\nValor: R$ 350,00",
  "imagemBase64": null,
  "carteirinhaBeneficiario": "0007654321"
}
```

Resposta (resumida):

```json
{
  "nomeArquivo": "recibo-consulta.pdf",
  "etapas": [
    { "ordem": 1, "nome": "Documento", "descricao": "Entrada do arquivo para o pipeline", "resultado": "recibo-consulta.pdf" }
  ],
  "dadosExtraidos": {
    "nomePaciente": "Joao da Silva",
    "cpfPaciente": "529.982.247-25",
    "prestador": "Dra. Ana Paula Ribeiro",
    "documentoPrestador": "11.222.333/0001-81",
    "registroProfissional": "CRM-SP 123456",
    "servico": "Consulta medica",
    "dataAtendimento": "2026-11-14",
    "valor": 350.00,
    "numeroRecibo": "1024"
  },
  "resultado": {
    "aprovado": true,
    "status": "APROVADO",
    "regrasAtendidas": ["Paciente confere com o beneficiario", "Paciente identificado", "..."],
    "pendencias": []
  }
}
```

O header `Location` da resposta aponta para `GET /api/documentos/{id}`. `mimeType` aceita `application/pdf`, `image/png` e `image/jpeg`; `imagemBase64` tem limite de 5 MB.

## Regras

Para um recibo ser aprovado, todas precisam ser atendidas:

- Paciente identificado, com CPF encontrado e válido (dígitos verificadores)
- Com carteirinha informada: paciente confere com o beneficiário do plano (nome e CPF)
- CPF ou CNPJ do prestador encontrado e válido
- Registro profissional encontrado e em formato válido (CRM, CRP ou CRO)
- Data do atendimento reconhecida, sem data futura e dentro de 12 meses (`app.validacao.prazo-atendimento-meses`)
- Valor maior que zero
- Recibo ainda não enviado (mesmo número e mesmo prestador)

Status:

- `APROVADO`: todas as regras atendidas.
- `REPROVADO`: alguma regra falhou (dado inválido, prazo vencido, paciente diferente do beneficiário, recibo duplicado), ou nada foi extraído.
- `REVISAO_MANUAL`: leitura incerta (só faltam campos e parte foi lida) ou valor acima do limite de reembolso do plano (`app.validacao.limite-reembolso`, padrão R$ 1.000,00). O analista confere o recibo original e aprova ou reprova pela tela (ou pela API), com motivo obrigatório; a decisão fica no histórico.

Beneficiários de exemplo (cadastro em memória): `0001234567` (Maria Souza) e `0007654321` (Joao da Silva).

## Como executar

Pré-requisito: JDK 21.

```bash
git clone https://github.com/micaellabcabral/IDP.git
cd IDP
./mvnw test                # Linux/macOS (mvnw.cmd no Windows)
./mvnw spring-boot:run     # http://localhost:8080, perfil dev com H2 em memória
```

Com a aplicação rodando, abra `http://localhost:8080` no navegador para usar o front-end.

### Integrações opcionais

OpenAI (a chave só vem da variável de ambiente):

```powershell
$env:OPENAI_API_KEY="sua-chave"
```

Tesseract, em `application.properties` ou por variável de ambiente:

```properties
app.ocr.command=tesseract
app.ocr.tessdata-path=C:/Program Files/Tesseract-OCR/tessdata
app.ocr.language=por
```

OpenCV, com o script incluído (recebe `arquivoEntrada arquivoSaida`; requer `pip install opencv-python`):

```properties
app.opencv.command=python scripts/preprocessar_documento.py
```

### Perfil prod com PostgreSQL

```powershell
$env:DB_PASSWORD="uma-senha-local"
docker compose up -d
$env:SPRING_PROFILES_ACTIVE="prod"
./mvnw spring-boot:run
```

Variáveis do perfil prod: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` e `APP_DADOS_CHAVE` (obrigatórias as duas últimas; a chave é o Base64 de 32 bytes, por exemplo `openssl rand -base64 32`).

## Fluxo para demo

1. Suba a aplicação com `./mvnw spring-boot:run` e abra `http://localhost:8080`.
2. Clique em **Usar exemplo** e depois em **Validar recibo**: o recibo sai **Aprovado**, conferido com o beneficiário da carteirinha.
3. Clique em **Validar recibo** de novo: o mesmo recibo é **Reprovado** como duplicado.
4. Na aba **Texto**, troque o valor por `R$ 1.500,00` e o número do recibo para ver **Revisão manual**; clique em **Revisar agora**, escreva o motivo e aprove ou reprove. Troque a carteirinha por `0001234567` para ver o paciente diferente do beneficiário.
5. Na aba **Arquivo**, envie um recibo real em PDF: o texto é lido direto, sem OCR, e prestador, paciente, CPF/CNPJ, serviço, data e valor são encontrados mesmo em recibo de texto corrido ("Eu, ..., recebi de ..., a quantia de R$ ...").
6. Mostre o **Histórico**, abra a tela **Banco de Dados**, expanda um registro e clique em **Baixar JSON**.

## Decisões técnicas

- **Processos externos em vez de bibliotecas Java**: Tesseract e OpenCV são chamados por comando configurável, com argumentos em lista (sem concatenar entrada do usuário), timeout de 30 segundos e arquivos temporários sempre apagados.
- **Fallback em todas as etapas**: erro ou timeout em PDF, OpenCV, OCR ou IA não derruba a requisição; a etapa usa o caminho local e informa a falha.
- **Privacidade com a IA**: CPF e CNPJ são mascarados antes de o texto ir para a OpenAI; CPF, CNPJ, registro profissional e valor são lidos localmente por regex.
- **Anti-alucinação**: paciente, prestador, serviço, data e número do recibo devolvidos pela IA só são aceitos se aparecem no texto do recibo.
- **Recibo duplicado sem guardar dados**: o banco guarda só um hash SHA-256 de número do recibo + documento do prestador para barrar reenvios.
- **Dados pessoais cifrados**: os dados extraídos do recibo e a carteirinha são gravados com AES-256-GCM numa coluna própria; quem abre o arquivo do banco só vê texto cifrado. A chave vem de `APP_DADOS_CHAVE` (no dev, é gerada uma vez em `data/chave-dev.key`, fora do Git). O texto completo e a imagem nunca são gravados, e o histórico da tela principal e `GET /api/documentos/{id}` não mostram dados pessoais.
- **Logs**: erros registram apenas o tipo da exceção e um conversor do Logback mascara qualquer CPF ou CNPJ que chegue a uma mensagem.
- **Front-end sem dependências**: HTML, CSS e JavaScript servidos pelo próprio Spring Boot, no estilo Notion; todo dado da API entra na página como texto, nunca como HTML, para evitar XSS.
- **Segredos**: chave da IA e senha do banco só por variável de ambiente; nada no repositório.

## Tecnologias

- Java 21, Spring Boot 3.5 (Web, Validation, Data JPA)
- PostgreSQL (prod) e H2 (dev e testes)
- Front-end em HTML, CSS e JavaScript, sem framework
- PDFBox para ler PDFs
- Tesseract OCR, OpenCV (Python) e API da OpenAI, como integrações opcionais
- JUnit 5, AssertJ e MockMvc; CI com GitHub Actions
- Maven (Maven Wrapper incluído)

## Estrutura

```
IDP/
├── src/main/java/br/com/mentoria/validacao/
│   ├── web/           # DocumentoController, ValidacaoRequest, ValidacaoResponse, TratadorDeErros
│   ├── dominio/       # DadosRecibo, Beneficiario, Cpf, Cnpj, RegistroProfissional, EtapaPipeline, ...
│   ├── servico/       # ValidacaoDocumentoService, PdfService, OpenCvService, OcrService,
│   │                  # InterpretadorIaService, ExtratorTexto, ValidadorRecibo, CadastroBeneficiarios
│   ├── persistencia/  # ValidacaoRegistro, ValidacaoRegistroRepository
│   ├── seguranca/     # Cifra (AES-256-GCM), CampoCriptografado
│   └── log/           # MascaraDadosPessoaisConverter
├── src/main/resources/  # application.properties, application-dev/prod.properties, logback-spring.xml
│   └── static/        # telas: index.html (Validação), banco.html (Banco de Dados), app.css, app.js, banco.js
├── scripts/preprocessar_documento.py
├── docker-compose.yml
└── pom.xml
```

## Autora

Micaella Cabral — [GitHub](https://github.com/micaellabcabral)
