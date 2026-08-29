import { inteiro } from '../formato';

/** Cada fonte com o proprio estado. Spinner mudo esconde qual site respondeu. */
export function SourceProgress({ fontes, buscando }) {
  if (!fontes || fontes.length === 0) {
    return buscando ? <p className="progresso">Consultando os sites…</p> : null;
  }
  return (
    <ul className="fontes">
      {fontes.map((fonte) => (
        <li key={fonte.source} className={`fonte ${fonte.health.toLowerCase()}`}>
          <b>{fonte.source}</b>
          <span>
            {fonte.health === 'OK'
              ? `${inteiro(fonte.offers)} ofertas`
              : fonte.message || 'sem resposta'}
          </span>
          <em className={fonte.demo ? 'simulado' : 'real'}>{fonte.demo ? 'simulado' : 'real'}</em>
        </li>
      ))}
    </ul>
  );
}
