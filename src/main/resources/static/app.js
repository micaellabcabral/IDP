// Tela de validacao de recibos. Todo dado vindo da API entra na pagina por textContent (nunca innerHTML), para evitar XSS.
const LIMITE_BYTES = 5 * 1024 * 1024;
const TIPOS = ["application/pdf", "image/png", "image/jpeg"];
const STATUS = { APROVADO: "Aprovado", REPROVADO: "Reprovado", REVISAO_MANUAL: "Revisão manual" };
const CAMPOS = [
  ["numeroRecibo", "Número do recibo"], ["nomePaciente", "Paciente"], ["cpfPaciente", "CPF do paciente"],
  ["prestador", "Profissional"], ["documentoPrestador", "CPF/CNPJ do prestador"],
  ["registroProfissional", "Registro profissional"], ["servico", "Serviço"],
  ["dataAtendimento", "Data do atendimento"], ["valor", "Valor"],
];
const REAIS = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

const $ = (id) => document.getElementById(id);
let modo = "arquivo";
let arquivo = null; // { nome, tipo, base64 }
let nomeArquivoTexto = "recibo.txt";
let ultimo = null;      // ultimo recibo validado: { id, nomeArquivo }
let emRevisao = null;   // recibo aberto no painel de revisao

function el(tag, texto, classe) {
  const e = document.createElement(tag);
  if (texto != null) e.textContent = texto;
  if (classe) e.className = classe;
  return e;
}

function icone(caminho) {
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("viewBox", "0 0 24 24");
  svg.setAttribute("aria-hidden", "true");
  const p = document.createElementNS("http://www.w3.org/2000/svg", "path");
  p.setAttribute("d", caminho);
  svg.appendChild(p);
  return svg;
}
const ICONE_OK = "M20 6 9 17l-5-5";
const ICONE_FALHA = "M18 6 6 18M6 6l12 12";

function formatar(chave, valor) {
  if (valor == null || valor === "") return null;
  if (chave === "dataAtendimento") {
    const [a, m, d] = valor.split("-");
    return `${d}/${m}/${a}`;
  }
  if (chave === "valor") return REAIS.format(valor);
  return String(valor);
}

function mostrarErro(mensagem) {
  $("erro").textContent = mensagem;
  $("erro").hidden = !mensagem;
}

function trocarAba(novo) {
  modo = novo;
  for (const nome of ["arquivo", "texto"]) {
    $("aba-" + nome).setAttribute("aria-selected", String(nome === novo));
    $("painel-" + nome).hidden = nome !== novo;
  }
  mostrarErro("");
}

function escolherArquivo(f) {
  arquivo = null;
  $("nome-arquivo").textContent = "Escolha ou arraste um PDF, PNG ou JPG de até 5 MB";
  if (!f) return;
  if (!TIPOS.includes(f.type)) return mostrarErro("Formato não aceito. Envie um PDF, PNG ou JPG.");
  if (f.size > LIMITE_BYTES) return mostrarErro("O arquivo passa de 5 MB. Envie um arquivo menor.");
  const leitor = new FileReader();
  leitor.onload = () => {
    arquivo = { nome: f.name, tipo: f.type, base64: String(leitor.result).split(",")[1] };
    $("nome-arquivo").textContent = f.name;
    mostrarErro("");
  };
  leitor.onerror = () => mostrarErro("Não foi possível ler o arquivo. Tente de novo.");
  leitor.readAsDataURL(f);
}

async function usarExemplo() {
  const r = await fetch("/api/documentos/exemplo");
  const exemplo = await r.json();
  nomeArquivoTexto = exemplo.nomeArquivo;
  $("conteudo").value = exemplo.conteudoDocumento;
  $("carteirinha").value = exemplo.carteirinhaBeneficiario || "";
  trocarAba("texto");
}

async function validar() {
  let corpo;
  if (modo === "arquivo") {
    if (!arquivo) return mostrarErro("Escolha um arquivo antes de validar.");
    corpo = { nomeArquivo: arquivo.nome, mimeType: arquivo.tipo, imagemBase64: arquivo.base64 };
  } else {
    const texto = $("conteudo").value.trim();
    if (!texto) return mostrarErro("Cole o texto do recibo antes de validar.");
    corpo = { nomeArquivo: nomeArquivoTexto, mimeType: "application/pdf", conteudoDocumento: texto };
  }
  const carteirinha = $("carteirinha").value.trim();
  if (carteirinha) corpo.carteirinhaBeneficiario = carteirinha;

  const botao = $("validar");
  botao.disabled = true;
  botao.textContent = "Validando...";
  mostrarErro("");
  try {
    const r = await fetch("/api/documentos/validar", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(corpo),
    });
    const json = await r.json().catch(() => ({}));
    if (!r.ok) {
      const erros = Array.isArray(json.erros) ? json.erros.join("\n") : json.detail;
      return mostrarErro(erros || "Não foi possível validar o recibo. Tente de novo.");
    }
    const location = r.headers.get("Location");
    ultimo = { id: location ? Number(location.split("/").pop()) : null, nomeArquivo: json.nomeArquivo };
    mostrarResultado(json);
    carregarHistorico();
  } catch {
    mostrarErro("Sem conexão com o servidor. Confira se a aplicação está rodando.");
  } finally {
    botao.disabled = false;
    botao.textContent = "Validar recibo";
  }
}

function mostrarResultado(resposta) {
  const { resultado, etapas, dadosExtraidos } = resposta;
  const status = $("status");
  status.className = "etiqueta " + resultado.status;
  status.textContent = STATUS[resultado.status] || resultado.status;
  const total = resultado.regrasAtendidas.length + resultado.pendencias.length;
  $("resumo").textContent = `${resultado.regrasAtendidas.length} de ${total} regras atendidas`;

  $("etapas").replaceChildren(...etapas.map((e) => {
    const li = el("li");
    li.dataset.ordem = e.ordem;
    li.append(el("strong", `${e.nome}: ${e.descricao}`), el("span", e.resultado ?? ""));
    return li;
  }));

  const dados = dadosExtraidos || {};
  $("dados").replaceChildren(...CAMPOS.flatMap(([chave, rotulo]) => {
    const valor = formatar(chave, dados[chave]);
    return [el("dt", rotulo), el("dd", valor || "Não encontrado", valor ? null : "vazio")];
  }));

  const regras = [
    ...resultado.regrasAtendidas.map((r) => ({ texto: r, ok: true })),
    ...resultado.pendencias.map((r) => ({ texto: r, ok: false })),
  ];
  $("regras").replaceChildren(...regras.map((r) => {
    const li = el("li", null, r.ok ? null : "falhou");
    li.append(icone(r.ok ? ICONE_OK : ICONE_FALHA), el("span", r.texto));
    return li;
  }));

  $("chamada-revisao").hidden = resultado.status !== "REVISAO_MANUAL" || !ultimo || !ultimo.id;
  $("revisao").hidden = true;

  $("resultado").hidden = false;
  $("resultado").scrollIntoView({ behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" });
}

async function carregarHistorico() {
  try {
    const r = await fetch("/api/documentos");
    const lista = r.ok ? await r.json() : [];
    $("historico").replaceChildren(...lista.map((v) => {
      const tr = el("tr");
      const status = el("td");
      status.append(el("span", STATUS[v.status] || v.status, "etiqueta " + v.status));
      if (v.revisadoManualmente) status.append(el("span", "decidido pelo analista", "revisado"));
      const pendencias = el("td", v.pendencias.length ? v.pendencias.join("; ") : "Nenhuma");
      if (v.motivoRevisao) pendencias.append(el("span", "Motivo: " + v.motivoRevisao, "revisado"));
      const acao = el("td");
      if (v.status === "REVISAO_MANUAL") {
        const botao = el("button", "Revisar", "botao");
        botao.type = "button";
        botao.addEventListener("click", () => abrirRevisao({ id: v.id, nomeArquivo: v.nomeArquivo }));
        acao.append(botao);
      }
      tr.append(
        el("td", new Date(v.criadoEm).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" })),
        el("td", v.nomeArquivo || "Sem nome"),
        status,
        pendencias,
        acao,
      );
      return tr;
    }));
    $("historico-vazio").hidden = lista.length > 0;
  } catch {
    $("historico-vazio").hidden = false;
  }
}

function abrirRevisao(recibo) {
  emRevisao = recibo;
  $("revisao-recibo").textContent = "#" + recibo.id + " " + (recibo.nomeArquivo || "");
  $("motivo").value = "";
  $("aviso-revisao").hidden = true;
  $("aviso-revisao").className = "aviso-erro";
  $("revisao").hidden = false;
  $("revisao").scrollIntoView({ behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" });
  $("motivo").focus({ preventScroll: true });
}

async function decidir(decisao) {
  const aviso = $("aviso-revisao");
  const motivo = $("motivo").value.trim();
  aviso.className = "aviso-erro";
  if (!motivo) {
    aviso.hidden = false;
    aviso.textContent = "Escreva o motivo da decisão.";
    return;
  }
  try {
    const r = await fetch("/api/documentos/" + encodeURIComponent(emRevisao.id) + "/revisao", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ decisao, motivo }),
    });
    const json = await r.json().catch(() => ({}));
    if (!r.ok) {
      aviso.hidden = false;
      aviso.textContent = Array.isArray(json.erros) ? json.erros.join("\n") : (json.detail || "Não foi possível registrar a decisão.");
      return;
    }
    if (ultimo && ultimo.id === emRevisao.id) {
      $("status").className = "etiqueta " + json.status;
      $("status").textContent = STATUS[json.status] + " na revisão manual";
      $("chamada-revisao").hidden = true;
    }
    aviso.hidden = false;
    aviso.className = "aviso-ok";
    aviso.textContent = decisao === "APROVADO" ? "Reembolso aprovado na revisão manual." : "Recibo reprovado na revisão manual.";
    setTimeout(() => { $("revisao").hidden = true; }, 1500);
    carregarHistorico();
  } catch {
    aviso.hidden = false;
    aviso.textContent = "Sem conexão com o servidor. Confira se a aplicação está rodando.";
  }
}

$("abrir-revisao").addEventListener("click", () => abrirRevisao(ultimo));
$("aprovar").addEventListener("click", () => decidir("APROVADO"));
$("reprovar").addEventListener("click", () => decidir("REPROVADO"));
$("cancelar-revisao").addEventListener("click", () => { $("revisao").hidden = true; });

$("aba-arquivo").addEventListener("click", () => trocarAba("arquivo"));
$("aba-texto").addEventListener("click", () => trocarAba("texto"));
$("arquivo").addEventListener("change", (e) => escolherArquivo(e.target.files[0]));
$("validar").addEventListener("click", validar);
$("exemplo").addEventListener("click", usarExemplo);
$("conteudo").addEventListener("input", () => { nomeArquivoTexto = "recibo.txt"; });

const area = $("area-arquivo");
area.addEventListener("dragover", (e) => { e.preventDefault(); area.classList.add("arrastando"); });
area.addEventListener("dragleave", () => area.classList.remove("arrastando"));
area.addEventListener("drop", (e) => {
  e.preventDefault();
  area.classList.remove("arrastando");
  escolherArquivo(e.dataTransfer.files[0]);
});

carregarHistorico();
