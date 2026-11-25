# IDP — Intelligent Medical Receipt Validation

<!-- badges -->
![Java](https://img.shields.io/badge/Java-ED8B00?style=flat-square&logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=flat-square&logo=springboot&logoColor=white) ![Maven](https://img.shields.io/badge/Maven-C71A36?style=flat-square&logo=apachemaven&logoColor=white) ![JUnit 5](https://img.shields.io/badge/JUnit%205-25A162?style=flat-square&logo=junit5&logoColor=white) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=flat-square&logo=postgresql&logoColor=white) ![HTML5](https://img.shields.io/badge/HTML5-E34F26?style=flat-square&logo=html5&logoColor=white) ![CSS](https://img.shields.io/badge/CSS-663399?style=flat-square&logo=css&logoColor=white) ![JavaScript](https://img.shields.io/badge/JavaScript-F7DF1E?style=flat-square&logo=javascript&logoColor=black) ![Python](https://img.shields.io/badge/Python-3776AB?style=flat-square&logo=python&logoColor=white)

[Ler em Português](README.md)

Java and Spring Boot system, with a web front end, to validate medical consultation receipts and invoices in health plan reimbursement requests. It takes a PDF or image, extracts the text, structures the data, checks it against the plan member and approves, rejects or sends the receipt to manual review. Built for the Avanade Mentorship 2026.2.

## Flow

```
Receipt (PDF/image)
   → PDF with text: read directly by PDFBox, no OCR (a scanned PDF goes on as an image)
   → OpenCV: image preprocessing                  (OpenCvService, external command)
   → Tesseract: text extraction                   (OcrService, external command)
   → AI: text into structured data                (InterpretadorIaService, OpenAI)
   → Business rules                               (ValidadorRecibo + plan member registry)
   → APROVADO | REPROVADO | REVISAO_MANUAL
```

Every stage has a local fallback, so the system works without an AI key and without Tesseract or OpenCV installed:

| Stage | When configured | When not configured or on failure |
| --- | --- | --- |
| OpenCV | `app.opencv.command` cleans up the image | keeps the original image |
| Tesseract | `app.ocr.tessdata-path` extracts text from the image | uses the text in `conteudoDocumento` |
| AI | `OPENAI_API_KEY` interprets the text | local regex extraction |

The `etapas` field of the response shows which path each stage took.

## Endpoints

| Method | Route | Description |
| --- | --- | --- |
| `GET` | `/` | Front end: validate a receipt and see the history |
| `GET` | `/api/documentos/exemplo` | Sample receipt (the visit is always 10 days ago) |
| `POST` | `/api/documentos/validar` | Runs the pipeline and returns stages, extracted data and result |
| `GET` | `/api/documentos` | History: the 20 most recent validations |
| `GET` | `/api/documentos/{id}` | Returns the stored result of a validation |

The request takes `nomeArquivo`, `mimeType`, `conteudoDocumento` and/or `imagemBase64`, plus an optional `carteirinhaBeneficiario` (plan member card). The response returns `etapas`, `dadosExtraidos` (patient, CPF, provider, CPF/CNPJ, professional registration, service, visit date, amount and receipt number) and `resultado` (`aprovado`, `status`, `regrasAtendidas`, `pendencias`). The `Location` header points to `GET /api/documentos/{id}`. `mimeType` accepts `application/pdf`, `image/png` and `image/jpeg`; `imagemBase64` is limited to 5 MB. See [README.md](README.md) for full JSON examples.

## Rules

A receipt is approved only if all of these hold (rule texts are returned in Portuguese):

- Patient identified, with a CPF (Brazilian taxpayer ID) found and valid (check digits)
- When a member card is sent: the patient matches the plan member (name and CPF)
- Provider CPF or CNPJ (company ID) found and valid
- Professional registration found and well formed (CRM, CRP or CRO)
- Visit date recognized, not in the future and within 12 months (`app.validacao.prazo-atendimento-meses`)
- Amount greater than zero
- Receipt not sent before (same number and same provider)

Status:

- `APROVADO`: every rule met.
- `REPROVADO`: a rule failed (invalid data, expired deadline, patient different from the member, duplicate receipt), or nothing was extracted.
- `REVISAO_MANUAL`: uncertain reading (only missing fields and something was read) or an amount above the plan reimbursement limit (`app.validacao.limite-reembolso`, default R$ 1,000.00).

Sample plan members (in-memory registry): `0001234567` (Maria Souza) and `0007654321` (Joao da Silva).

## Running

Requirement: JDK 21.

```bash
git clone https://github.com/micaellabcabral/IDP.git
cd IDP
./mvnw test                # Linux/macOS (mvnw.cmd on Windows)
./mvnw spring-boot:run     # http://localhost:8080, dev profile with in-memory H2
```

With the application running, open `http://localhost:8080` in a browser to use the front end.

### Optional integrations

OpenAI (the key only comes from an environment variable):

```powershell
$env:OPENAI_API_KEY="your-key"
```

Tesseract, in `application.properties` or through environment variables:

```properties
app.ocr.command=tesseract
app.ocr.tessdata-path=C:/Program Files/Tesseract-OCR/tessdata
app.ocr.language=por
```

OpenCV, with the bundled script (takes `inputFile outputFile`; requires `pip install opencv-python`):

```properties
app.opencv.command=python scripts/preprocessar_documento.py
```

### prod profile with PostgreSQL

```powershell
$env:DB_PASSWORD="a-local-password"
docker compose up -d
$env:SPRING_PROFILES_ACTIVE="prod"
./mvnw spring-boot:run
```

prod profile variables: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` and `DB_PASSWORD` (required).

## Demo flow

1. Start the application with `./mvnw spring-boot:run` and open `http://localhost:8080`.
2. Click **Usar exemplo** and then **Validar recibo**: the receipt comes out **Aprovado**, checked against the member card.
3. Click **Validar recibo** again: the same receipt is **Reprovado** as a duplicate.
4. In the **Texto** tab, change the amount to `R$ 1.500,00` and the receipt number to get **Revisão manual**, or change the member card to `0001234567` to show a patient who is not the member.
5. In the **Arquivo** tab, upload a PDF with text: it is read directly, without OCR.
6. Show the **Histórico**: it keeps only the result, no personal data.

## Technical decisions

- **External processes instead of Java libraries**: Tesseract and OpenCV run as configurable commands, with arguments passed as a list (no concatenated user input), a 30-second timeout and temporary files always deleted.
- **Fallback at every stage**: an error or timeout in PDF, OpenCV, OCR or AI does not fail the request; the stage takes the local path and reports the failure.
- **Privacy with the AI**: CPF and CNPJ are masked before the text is sent to OpenAI; CPF, CNPJ, professional registration and amount are read locally with regexes.
- **Anti-hallucination**: patient, provider, service, date and receipt number returned by the AI are only accepted if they appear in the receipt text.
- **Duplicate receipts without storing data**: the database keeps only a SHA-256 hash of receipt number + provider document to block resubmissions.
- **Data minimization**: the database stores only the file name, status, pending rules, timestamp and the hash; never the text, the image, the name, the CPF, the CNPJ or the amount.
- **Logs**: errors only record the exception type, and a Logback converter masks any CPF or CNPJ that reaches a log message.
- **Dependency-free front end**: HTML, CSS and JavaScript served by Spring Boot itself, in a Notion-like style; all API data goes into the page as text, never as HTML, to prevent XSS.
- **Secrets**: the AI key and the database password come only from environment variables; nothing is stored in the repository.

## Tech stack

- Java 21, Spring Boot 3.5 (Web, Validation, Data JPA)
- PostgreSQL (prod) and H2 (dev and tests)
- Front end in HTML, CSS and JavaScript, no framework
- PDFBox to read PDFs
- Tesseract OCR, OpenCV (Python) and the OpenAI API, as optional integrations
- JUnit 5, AssertJ and MockMvc; CI with GitHub Actions
- Maven (Maven Wrapper included)

## Author

Micaella Cabral — [GitHub](https://github.com/micaellabcabral)
