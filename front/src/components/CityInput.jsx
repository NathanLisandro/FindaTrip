import { useEffect, useRef, useState } from 'react';

/**
 * Ninguém sabe que Maringá é MGF. Aqui se digita a cidade e escolhe na lista;
 * o código fica guardado por baixo.
 */
export function CityInput({ rotulo, valor, onEscolher, placeholder }) {
  const [texto, setTexto] = useState('');
  const [opcoes, setOpcoes] = useState([]);
  const [aberto, setAberto] = useState(false);
  const [destacado, setDestacado] = useState(0);
  const caixa = useRef(null);

  useEffect(() => {
    function foraDaCaixa(evento) {
      if (caixa.current && !caixa.current.contains(evento.target)) setAberto(false);
    }
    document.addEventListener('mousedown', foraDaCaixa);
    return () => document.removeEventListener('mousedown', foraDaCaixa);
  }, []);

  useEffect(() => {
    if (texto.trim().length < 2) { setOpcoes([]); return; }
    const tempo = setTimeout(async () => {
      try {
        const resposta = await fetch(`/api/places?q=${encodeURIComponent(texto)}&limite=8`);
        if (resposta.ok) { setOpcoes(await resposta.json()); setDestacado(0); }
      } catch { /* autocomplete que falha não pode travar a busca */ }
    }, 180);
    return () => clearTimeout(tempo);
  }, [texto]);

  const escolher = (aeroporto) => {
    onEscolher(aeroporto);
    setTexto('');
    setAberto(false);
  };

  const teclado = (evento) => {
    if (!aberto || opcoes.length === 0) return;
    if (evento.key === 'ArrowDown') { evento.preventDefault(); setDestacado((d) => (d + 1) % opcoes.length); }
    if (evento.key === 'ArrowUp') { evento.preventDefault(); setDestacado((d) => (d - 1 + opcoes.length) % opcoes.length); }
    if (evento.key === 'Enter') { evento.preventDefault(); escolher(opcoes[destacado]); }
    if (evento.key === 'Escape') setAberto(false);
  };

  return (
    <div className="cidade" ref={caixa}>
      <span className="cidade__rotulo">{rotulo}</span>
      <input
        value={aberto ? texto : (valor ? `${valor.city} (${valor.code})` : texto)}
        placeholder={placeholder}
        onFocus={() => { setAberto(true); setTexto(''); }}
        onChange={(e) => { setTexto(e.target.value); setAberto(true); }}
        onKeyDown={teclado}
        autoComplete="off"
      />
      {aberto && opcoes.length > 0 && (
        <ul className="cidade__lista" role="listbox">
          {opcoes.map((opcao, indice) => (
            <li key={opcao.code}>
              <button
                type="button"
                className={`cidade__opcao ${indice === destacado ? 'destacada' : ''}`}
                onMouseEnter={() => setDestacado(indice)}
                onClick={() => escolher(opcao)}
              >
                <span className="cidade__cidade">{opcao.city}</span>
                <span className="cidade__codigo">{opcao.code}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
