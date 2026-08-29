export const brl = (valor) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(valor);

export const nota = (valor) =>
  new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 }).format(valor);

export const inteiro = (valor) => new Intl.NumberFormat('pt-BR').format(valor);

export const emDias = (dias) =>
  new Date(Date.now() + dias * 86400000).toISOString().slice(0, 10);

export const ROTULOS = {
  BEST_VALUE_OVERALL: 'Pacote Ideal',
  SMART_BUDGET: 'Econômico Inteligente',
  MAX_COMFORT: 'Máximo Conforto',
};
