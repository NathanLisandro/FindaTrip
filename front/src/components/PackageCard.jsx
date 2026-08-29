import { brl, inteiro, nota, ROTULOS } from '../formato';

const SELO = {
  BEST_VALUE_OVERALL: 'melhor',
  SMART_BUDGET: 'economico',
  MAX_COMFORT: 'conforto',
};

export function PackageCard({ pacote, destaque }) {
  const oculto = Number(pacote.hiddenCosts) > 0;
  const temNota = pacote.lodgingReviews > 0;

  return (
    <article className={`cartao ${destaque ? 'cartao--destaque' : ''}`}>
      <div className="cartao__foto">
        {pacote.image
          ? <img src={pacote.image} alt="" loading="lazy" />
          : <div className="cartao__foto--vazia" aria-hidden="true">sem foto</div>}
        <span className={`selo selo--${SELO[pacote.recommendationType] || 'melhor'}`}>
          {ROTULOS[pacote.recommendationType] || pacote.recommendationType}
        </span>
      </div>

      <div className="cartao__corpo">
        <header className="cartao__topo">
          <div>
            <h3>{pacote.lodgingName}</h3>
            <p className="cartao__local">
              {pacote.neighborhood}
              <span className="ponto">·</span>
              <span className="fonte-tag">{pacote.lodgingSource}</span>
            </p>
          </div>
          <div className="cartao__preco">
            {oculto && <s>{brl(pacote.advertisedPrice)}</s>}
            <strong>{brl(pacote.realCost)}</strong>
            <small>total, tudo somado</small>
          </div>
        </header>

        {oculto && (
          <p className="alerta-oculto">
            <b>{brl(pacote.hiddenCosts)}</b> que o anúncio não mostra
          </p>
        )}

        <div className="cartao__linha">
          {temNota ? (
            <span className="pastilha pastilha--nota">
              <b>{nota(pacote.lodgingRating)}</b> {inteiro(pacote.lodgingReviews)} avaliações
            </span>
          ) : (
            <span className="pastilha pastilha--sem-nota">sem avaliações ainda</span>
          )}
          <span className="pastilha">
            ✈ {pacote.airline} · {pacote.stops === 0 ? 'direto' : `${pacote.stops} parada`}
          </span>
          {pacote.carSupplier && (
            <span className="pastilha">🚗 {pacote.carSupplier} · {pacote.carCategory}</span>
          )}
        </div>

        {pacote.included.length > 0 && (
          <p className="incluido"><span>Já incluso:</span> {pacote.included.join(' · ')}</p>
        )}

        {pacote.why && <p className="porque">{pacote.why}</p>}

        <footer className="cartao__acoes">
          {pacote.links.map((link, indice) => (
            <a
              key={link.partner}
              className={`botao ${indice === 0 ? 'botao--principal' : 'botao--suave'}`}
              href={link.url}
              target="_blank"
              rel="noopener noreferrer"
            >
              {link.partner}
            </a>
          ))}
        </footer>
      </div>
    </article>
  );
}
