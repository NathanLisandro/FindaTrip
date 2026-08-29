import { brl, inteiro } from '../formato';

/**
 * Filtros na lateral, como nos comparadores de viagem: cada opção mostra quantos
 * pacotes tem e a partir de quanto, então dá para escolher sem tentativa e erro.
 * As contagens vêm de TODOS os candidatos, não do resultado filtrado — senão as
 * opções somem conforme se filtra e não há como voltar.
 */
export function FilterSidebar({ bairros, tipos, filtros, bairro, tiposEscolhidos, onFiltros, onBairro, onTipos }) {
  const alternarTipo = (tipo) => {
    const novos = tiposEscolhidos.includes(tipo)
      ? tiposEscolhidos.filter((t) => t !== tipo)
      : [...tiposEscolhidos, tipo];
    onTipos(novos);
  };

  const limpar = () => {
    onFiltros({});
    onBairro(null);
    onTipos([]);
  };

  const temFiltro = bairro || tiposEscolhidos.length > 0 || Object.values(filtros).some(Boolean);

  return (
    <aside className="lateral">
      <div className="lateral__topo">
        <h2>Filtros</h2>
        {temFiltro && <button type="button" className="limpar" onClick={limpar}>Limpar</button>}
      </div>

      {tipos?.length > 0 && (
        <section className="grupo">
          <h3>Tipo de hospedagem</h3>
          {tipos.map((tipo) => (
            <label key={tipo.type} className="opcao">
              <input
                type="checkbox"
                checked={tiposEscolhidos.includes(tipo.type)}
                onChange={() => alternarTipo(tipo.type)}
              />
              <span className="opcao__nome">{tipo.label}</span>
              <span className="opcao__contagem">{inteiro(tipo.packages)}</span>
            </label>
          ))}
        </section>
      )}

      {bairros?.length > 0 && (
        <section className="grupo">
          <h3>Bairro</h3>
          <label className="opcao">
            <input type="radio" name="bairro" checked={!bairro} onChange={() => onBairro(null)} />
            <span className="opcao__nome">Todos os bairros</span>
          </label>
          {bairros.map((area) => (
            <label key={area.neighborhood} className="opcao">
              <input
                type="radio"
                name="bairro"
                checked={bairro === area.neighborhood}
                onChange={() => onBairro(area.neighborhood)}
              />
              <span className="opcao__nome">
                {area.neighborhood}
                <small>a partir de {brl(area.cheapest)}</small>
              </span>
              <span className="opcao__contagem">{inteiro(area.packages)}</span>
            </label>
          ))}
        </section>
      )}

      <section className="grupo">
        <h3>Preço e nota</h3>
        <label className="campo">
          <span className="campo__rotulo">Preço máximo</span>
          <input
            type="number" min="0" step="100" placeholder="sem limite"
            value={filtros.maxPrice || ''}
            onChange={(e) => onFiltros({ ...filtros, maxPrice: e.target.value })}
          />
        </label>
        <label className="campo">
          <span className="campo__rotulo">Nota mínima</span>
          <input
            type="number" min="0" max="10" step="0.5" placeholder="qualquer"
            value={filtros.minRating || ''}
            onChange={(e) => onFiltros({ ...filtros, minRating: e.target.value })}
          />
        </label>
      </section>

      <section className="grupo">
        <h3>Comodidades</h3>
        <label className="opcao">
          <input type="checkbox" checked={!!filtros.breakfastIncluded}
                 onChange={(e) => onFiltros({ ...filtros, breakfastIncluded: e.target.checked })} />
          <span className="opcao__nome">Café da manhã incluso</span>
        </label>
        <label className="opcao">
          <input type="checkbox" checked={!!filtros.freeCancellation}
                 onChange={(e) => onFiltros({ ...filtros, freeCancellation: e.target.checked })} />
          <span className="opcao__nome">Cancelamento grátis</span>
        </label>
        <label className="opcao">
          <input type="checkbox" checked={!!filtros.directFlightOnly}
                 onChange={(e) => onFiltros({ ...filtros, directFlightOnly: e.target.checked })} />
          <span className="opcao__nome">Só voo direto</span>
        </label>
      </section>
    </aside>
  );
}
