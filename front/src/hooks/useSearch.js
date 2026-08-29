import { useCallback, useRef, useState } from 'react';

const INTERVALO_MS = 1500;
const MAX_TENTATIVAS = 60;

/**
 * Dispara a busca, acompanha o progresso e reaplica filtros sem refazer a busca.
 * A busca e assincrona no servidor: o POST devolve um id na hora e o GET vai
 * dizendo o que cada fonte ja respondeu.
 */
export function useSearch() {
  const [resultado, setResultado] = useState(null);
  const [buscando, setBuscando] = useState(false);
  const [erro, setErro] = useState(null);
  const idRef = useRef(null);
  const filtrosRef = useRef({});

  const ler = useCallback(async (filtros) => {
    if (!idRef.current) return null;
    const params = new URLSearchParams();
    Object.entries(filtros || {}).forEach(([chave, valor]) => {
      if (valor !== '' && valor !== false && valor != null) params.set(chave, valor);
    });
    const resposta = await fetch(`/api/search/${idRef.current}?${params}`);
    if (!resposta.ok) {
      setErro('Essa busca expirou. Faça uma nova.');
      setBuscando(false);
      return null;
    }
    const dados = await resposta.json();
    setResultado(dados);
    return dados;
  }, []);

  const acompanhar = useCallback(async (tentativa = 0) => {
    const dados = await ler(filtrosRef.current);
    if (dados && dados.status === 'BUSCANDO' && tentativa < MAX_TENTATIVAS) {
      setTimeout(() => acompanhar(tentativa + 1), INTERVALO_MS);
    } else {
      setBuscando(false);
    }
  }, [ler]);

  const buscar = useCallback(async (criterio) => {
    setErro(null);
    setResultado(null);
    setBuscando(true);
    filtrosRef.current = {};
    const resposta = await fetch('/api/search', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(criterio),
    });
    if (!resposta.ok) {
      setErro('Não foi possível iniciar a busca. Confira os campos e tente de novo.');
      setBuscando(false);
      return;
    }
    idRef.current = (await resposta.json()).searchId;
    acompanhar();
  }, [acompanhar]);

  const filtrar = useCallback((filtros) => {
    filtrosRef.current = filtros;
    return ler(filtros);
  }, [ler]);

  return { resultado, buscando, erro, buscar, filtrar, temBusca: Boolean(idRef.current) };
}
