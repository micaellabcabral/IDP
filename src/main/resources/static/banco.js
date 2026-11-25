// Tela Banco de Dados. Todo dado vindo da API entra na pagina por textContent (nunca innerHTML), para evitar XSS.
const STATUS = { APROVADO: "Aprovado", REPROVADO: "Reprovado", REVISAO_MANUAL: "Revisão manual" };
const CAMPOS = [
  ["numeroRecibo", "Número do recibo"], ["nomePaciente", "Paciente"], ["cpfPaciente", "CPF do paciente"],
  ["prestador", "Profissional"], ["documentoPrestador", "CPF/CNPJ do prestador"],
  ["registroProfissional", "Registro profissional"], ["servico", "Serviço"],
  ["dataAtendimento", "Data do atendimento"], ["valor", "Valor"],
];
const REAIS = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

const $ = (id) => document.getElementById(id);
let todos = [];
let filtroStatus = "";

function el(tag, texto, classe) {
  const e = document.createElement(tag);
  if (texto != null) e.textContent = texto;
  if (classe) e.className = classe;
  return e;
}

function data(valor) {
  if (!valor) return null;
  const [a, m, d] = valor.split("-");
  return `${d}/${m}/${a}`;
}

function quando(instante) {
  return instante ? new Date(instante).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" }) : null;
}

function formatar(chave, valor) {
  if (valor == null || valor === "") return null;
  if (chave === "dataAtendimento") return data(valor);
  if (chave === "valor") return REAIS.format(valor);
  return String(valor);
}

function seta() {
  const ns = "http://www.w3.org/2000/svg";
  const svg = document.createElementNS(ns, "svg");
  svg.setAttribute("viewBox", "0 0 24 24");
  svg.setAttribute("aria-hidden", "true");
  const p = document.createElementNS(ns, "path");
  p.setAttribute("d", "m9 6 6 6-6 6");
  svg.appendChild(p);
  return svg;
}

function propriedades(pares) {
  const dl = el("dl", null, "propriedades");
  pares.forEach(([rotulo, valor]) => dl.append(el("dt", rotulo), el("dd", valor || "-", valor ? null : "vazio")));
  return dl;
}

function baixar(nome, objeto) {
  const url = URL.createObjectURL(new Blob([JSON.stringify(objeto, null, 2)], { type: "application/json" }));
  const a = el("a");
  a.href = url;
  a.download = nome;
  document.body.append(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

function linhaDetalhe(r) {
  const tr = el("tr", null, "detalhe");
  tr.hidden = true;
  const td = el("td");
  td.colSpan = 10;
  const grade = el("div", null, "detalhe-grade");

  const recibo = el("div");
  recibo.append(el("h3", "Recibo"), propriedades([
    ...CAMPOS.map(([chave, rotulo]) => [rotulo, formatar(chave, (r.recibo || {})[chave])]),
    ["Carteirinha", r.carteirinhaBeneficiario],
  ]));

  const resultado = el("div");
  const pares = [
    ["Status", STATUS[r.status] || r.status],
    ["Pendências", r.pendencias.length ? r.pendencias.join("; ") : "Nenhuma"],
    ["Validado em", quando(r.criadoEm)],
  ];
  if (r.revisadoManualmente) {
    pares.push(["Revisão", "Decidido pelo analista em " + quando(r.revisadoEm)], ["Motivo", r.motivoRevisao]);
  }
  resultado.append(el("h3", "Resultado"), propriedades(pares));
  grade.append(recibo, resultado);

  const acoes = el("div", null, "acoes");
  const botao = el("button", "Baixar JSON deste registro", "botao");
  botao.type = "button";
  botao.addEventListener("click", () => baixar(`idp-reembolso-${r.id}.json`, r));
  acoes.append(botao);
  td.append(grade, acoes);
  tr.append(td);
  return tr;
}

function linha(r) {
  const tr = el("tr");
  const status = el("td");
  status.append(el("span", STATUS[r.status] || r.status, "etiqueta " + r.status));
  if (r.revisadoManualmente) status.append(el("span", "decidido pelo analista", "revisado"));
  const c = r.recibo || {};
  const detalhe = linhaDetalhe(r);

  const expandir = el("button", null, "expandir");
  expandir.type = "button";
  expandir.setAttribute("aria-expanded", "false");
  expandir.setAttribute("aria-label", "Ver dados do registro " + r.id);
  expandir.append(seta());
  expandir.addEventListener("click", () => {
    detalhe.hidden = !detalhe.hidden;
    expandir.setAttribute("aria-expanded", String(!detalhe.hidden));
  });
  const acao = el("td");
  acao.append(expandir);

  tr.append(el("td", r.id, "num"), el("td", r.nomeArquivo || "Sem nome"), status,
    el("td", c.nomePaciente || "-"), el("td", c.prestador || "-"),
    el("td", formatar("valor", c.valor) || "-"), el("td", data(c.dataAtendimento) || "-"),
    el("td", r.pendencias.length ? r.pendencias.join("; ") : "Nenhuma", "quebra"),
    el("td", quando(r.criadoEm)), acao);
  return [tr, detalhe];
}

function normal(s) {
  return (s || "").normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();
}

function desenhar() {
  const busca = normal($("busca").value.trim());
  const visiveis = todos.filter((r) => {
    if (filtroStatus && r.status !== filtroStatus) return false;
    if (!busca) return true;
    const c = r.recibo || {};
    return [r.nomeArquivo, c.nomePaciente, c.prestador, c.cpfPaciente, c.documentoPrestador]
      .some((v) => normal(v).includes(busca));
  });
  const corpo = $("registros");
  corpo.replaceChildren();
  if (!visiveis.length) {
    const tr = el("tr");
    const td = el("td", todos.length ? "Nenhum registro com esse filtro."
      : "Nenhum recibo validado ainda. Valide um recibo na tela Validação.", "nota");
    td.colSpan = 10;
    tr.append(td);
    corpo.append(tr);
  }
  visiveis.forEach((r) => corpo.append(...linha(r)));
  $("contagem").textContent = `${visiveis.length} de ${todos.length} registros`;
}

async function carregar() {
  try {
    const r = await fetch("/api/banco");
    if (!r.ok) throw new Error();
    todos = await r.json();
    $("erro").hidden = true;
    desenhar();
  } catch {
    $("erro").hidden = false;
    $("erro").textContent = "Não foi possível carregar o banco de dados. Confira se a aplicação está rodando.";
  }
}

document.querySelectorAll(".filtro").forEach((b) => b.addEventListener("click", () => {
  filtroStatus = b.dataset.status;
  document.querySelectorAll(".filtro").forEach((f) => f.setAttribute("aria-pressed", String(f === b)));
  desenhar();
}));
$("busca").addEventListener("input", desenhar);
$("atualizar").addEventListener("click", carregar);
carregar();
