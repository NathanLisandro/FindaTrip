/** Os filtros sao regra de negocio e rodam no servidor; aqui so viram query params. */
export function FilterBar({ filtros, onMudar }) {
  const mudar = (campo) => (evento) => {
    const alvo = evento.target;
    onMudar({ ...filtros, [campo]: alvo.type === 'checkbox' ? alvo.checked : alvo.value });
  };
  return (
    <section className="filtros">
      <label>Preço máximo<input type="number" min="0" step="50" value={filtros.maxPrice || ''} onChange={mudar('maxPrice')} /></label>
      <label>Nota mínima<input type="number" min="0" max="10" step="0.5" value={filtros.minRating || ''} onChange={mudar('minRating')} /></label>
      <label className="check"><input type="checkbox" checked={!!filtros.directFlightOnly} onChange={mudar('directFlightOnly')} /> Só voo direto</label>
      <label className="check"><input type="checkbox" checked={!!filtros.breakfastIncluded} onChange={mudar('breakfastIncluded')} /> Café da manhã</label>
      <label className="check"><input type="checkbox" checked={!!filtros.freeCancellation} onChange={mudar('freeCancellation')} /> Cancelamento grátis</label>
    </section>
  );
}
