import { brl, inteiro } from '../formato';

/** Bairros vindos de TODOS os candidatos, entao as opcoes nao somem conforme se filtra. */
export function NeighborhoodChips({ bairros, selecionado, onSelecionar }) {
  if (!bairros || bairros.length === 0) return null;
  return (
    <section className="bairros">
      <h2>Em que parte do destino?</h2>
      <div className="chips">
        <button
          type="button"
          className={`chip ${selecionado ? '' : 'ativo'}`}
          onClick={() => onSelecionar(null)}
        >
          Todos os bairros
        </button>
        {bairros.map((bairro) => (
          <button
            key={bairro.neighborhood}
            type="button"
            className={`chip ${selecionado === bairro.neighborhood ? 'ativo' : ''}`}
            onClick={() => onSelecionar(bairro.neighborhood)}
          >
            {bairro.neighborhood}
            <small>{inteiro(bairro.packages)} {bairro.packages === 1 ? 'opção' : 'opções'} · a partir de {brl(bairro.cheapest)}</small>
          </button>
        ))}
      </div>
    </section>
  );
}
