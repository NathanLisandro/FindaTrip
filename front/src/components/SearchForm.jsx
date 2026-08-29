import { useState } from 'react';
import { emDias } from '../formato';
import { CityInput } from './CityInput';

const MARINGA = { code: 'MGF', city: 'Maringá' };
const FLORIPA = { code: 'FLN', city: 'Florianópolis' };

export function SearchForm({ onBuscar, buscando }) {
  const [origem, setOrigem] = useState(MARINGA);
  const [destino, setDestino] = useState(FLORIPA);
  const [datas, setDatas] = useState({ ida: emDias(45), volta: emDias(52) });
  const [viajantes, setViajantes] = useState(2);
  const [opcoes, setOpcoes] = useState({ checkedBagRequested: false, carRequired: false, flexibleDates: false });

  const marcar = (campo) => (evento) => setOpcoes({ ...opcoes, [campo]: evento.target.checked });

  return (
    <form
      className="busca"
      onSubmit={(evento) => {
        evento.preventDefault();
        if (!origem || !destino) return;
        onBuscar({
          origin: origem.code,
          destination: destino.city,
          departureDate: datas.ida,
          returnDate: datas.volta,
          travelers: Number(viajantes),
          ...opcoes,
        });
      }}
    >
      <div className="busca__campos">
        <CityInput rotulo="Origem" valor={origem} onEscolher={setOrigem} placeholder="Digite a cidade" />
        <CityInput rotulo="Destino" valor={destino} onEscolher={setDestino} placeholder="Digite a cidade" />
        <label className="campo">
          <span className="campo__rotulo">Ida</span>
          <input type="date" value={datas.ida} onChange={(e) => setDatas({ ...datas, ida: e.target.value })} required />
        </label>
        <label className="campo">
          <span className="campo__rotulo">Volta</span>
          <input type="date" value={datas.volta} onChange={(e) => setDatas({ ...datas, volta: e.target.value })} required />
        </label>
        <label className="campo campo--curto">
          <span className="campo__rotulo">Pessoas</span>
          <input type="number" min="1" max="9" value={viajantes} onChange={(e) => setViajantes(e.target.value)} required />
        </label>
        <button type="submit" disabled={buscando}>{buscando ? 'Buscando…' : 'Buscar'}</button>
      </div>

      <div className="busca__opcoes">
        <label className="check"><input type="checkbox" checked={opcoes.checkedBagRequested} onChange={marcar('checkedBagRequested')} /> Bagagem despachada</label>
        <label className="check"><input type="checkbox" checked={opcoes.carRequired} onChange={marcar('carRequired')} /> Preciso de carro</label>
        <label className="check"><input type="checkbox" checked={opcoes.flexibleDates} onChange={marcar('flexibleDates')} /> Datas flexíveis (±3 dias)</label>
      </div>
    </form>
  );
}
