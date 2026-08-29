/**
 * Aviso por fonte, nao global.
 * Com voo e hospedagem reais e carro simulado, dizer "tudo e demonstracao" seria
 * falso, e esconder seria pior: um buscador que mostra preco inventado sem avisar
 * e pior que nao existir.
 */
export function DemoNotice({ fontes }) {
  const simuladas = (fontes || []).filter((fonte) => fonte.demo).map((fonte) => fonte.source);
  if (simuladas.length === 0) return null;
  return (
    <div className="aviso-demo" role="status">
      Dados simulados em: <b>{simuladas.join(', ')}</b>. Esses preços não são reais.
    </div>
  );
}
