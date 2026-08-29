import { brl, inteiro, nota, ROTULOS } from '../formato';

export function PackageCard({ pacote, destaque }) {
  const oculto = Number(pacote.hiddenCosts) > 0;
  return (
    <article className={`pacote ${destaque ? 'destaque' : ''}`}>
      <header>
        <span className="etiqueta">{ROTULOS[pacote.recommendationType] || pacote.recommendationType}</span>
        <div className="preco">
          <strong>{brl(pacote.realCost)}</strong>
          {oculto && <s>{brl(pacote.advertisedPrice)}</s>}
        </div>
      </header>

      {oculto && (
        <p className="custo-oculto">
          {brl(pacote.hiddenCosts)} em custos que o anúncio não mostra
        </p>
      )}

      <dl className="detalhes">
        <div>
          <dt>Hospedagem</dt>
          <dd>
            {pacote.lodgingName} — {pacote.neighborhood}
            {pacote.lodgingReviews > 0
              ? ` — nota ${nota(pacote.lodgingRating)} (${inteiro(pacote.lodgingReviews)} avaliações)`
              : ' — sem avaliações ainda'}
          </dd>
        </div>
        <div>
          <dt>Voo</dt>
          <dd>{pacote.airline} — {pacote.stops === 0 ? 'direto' : `${pacote.stops} parada(s)`}</dd>
        </div>
        {pacote.carSupplier && (
          <div><dt>Carro</dt><dd>{pacote.carSupplier} — {pacote.carCategory}</dd></div>
        )}
        <div><dt>Incluído</dt><dd>{pacote.included.join(', ') || '—'}</dd></div>
      </dl>

      {pacote.why && <p className="porque"><b>Por que este pacote:</b> {pacote.why}</p>}

      <footer>
        {pacote.links.map((link) => (
          <a key={link.partner} className="reservar" href={link.url} target="_blank" rel="noopener noreferrer">
            Reservar no {link.partner}
          </a>
        ))}
      </footer>
    </article>
  );
}
