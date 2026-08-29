import { useState } from 'react';
import { emDias } from '../formato';

export function SearchForm({ onBuscar, buscando }) {
  const [criterio, setCriterio] = useState({
    origin: 'MGF',
    destination: 'Florianópolis',
    departureDate: emDias(45),
    returnDate: emDias(52),
    travelers: 2,
    checkedBagRequested: true,
    carRequired: false,
    flexibleDates: false,
  });

  const mudar = (campo) => (evento) => {
    const alvo = evento.target;
    setCriterio({ ...criterio, [campo]: alvo.type === 'checkbox' ? alvo.checked : alvo.value });
  };

  return (
    <form
      className="busca"
      onSubmit={(evento) => {
        evento.preventDefault();
        onBuscar({ ...criterio, travelers: Number(criterio.travelers) });
      }}
    >
      <label>Origem<input value={criterio.origin} onChange={mudar('origin')} maxLength={3} required /></label>
      <label>Destino<input value={criterio.destination} onChange={mudar('destination')} required /></label>
      <label>Ida<input type="date" value={criterio.departureDate} onChange={mudar('departureDate')} required /></label>
      <label>Volta<input type="date" value={criterio.returnDate} onChange={mudar('returnDate')} required /></label>
      <label>Viajantes<input type="number" min="1" value={criterio.travelers} onChange={mudar('travelers')} required /></label>
      <label className="check"><input type="checkbox" checked={criterio.checkedBagRequested} onChange={mudar('checkedBagRequested')} /> Bagagem despachada</label>
      <label className="check"><input type="checkbox" checked={criterio.carRequired} onChange={mudar('carRequired')} /> Preciso de carro</label>
      <label className="check"><input type="checkbox" checked={criterio.flexibleDates} onChange={mudar('flexibleDates')} /> Datas flexíveis (±3 dias)</label>
      <button type="submit" disabled={buscando}>{buscando ? 'Buscando…' : 'Buscar'}</button>
    </form>
  );
}
