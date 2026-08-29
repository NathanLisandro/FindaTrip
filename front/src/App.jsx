import { useState } from 'react';
import { useSearch } from './hooks/useSearch';
import { SearchForm } from './components/SearchForm';
import { SourceProgress } from './components/SourceProgress';
import { DemoNotice } from './components/DemoNotice';
import { NeighborhoodChips } from './components/NeighborhoodChips';
import { FilterBar } from './components/FilterBar';
import { PackageCard } from './components/PackageCard';
import { DateOptions } from './components/DateOptions';

export default function App() {
  const { resultado, buscando, erro, buscar, filtrar } = useSearch();
  const [filtros, setFiltros] = useState({});
  const [bairro, setBairro] = useState(null);

  const aplicar = (novos, novoBairro = bairro) => {
    setFiltros(novos);
    setBairro(novoBairro);
    filtrar({ ...novos, neighborhood: novoBairro || '' });
  };

  const degradadas = (resultado?.sources || [])
    .filter((fonte) => fonte.health === 'DEGRADADO')
    .map((fonte) => fonte.source);

  return (
    <main className="tela">
      <header className="cabecalho">
        <h1>Para onde e quando?</h1>
        <p>Compara o custo real da viagem — depois de somar taxas, bagagem e seguro.</p>
      </header>
      <DemoNotice fontes={resultado?.sources} />

      <SearchForm onBuscar={(criterio) => { setFiltros({}); setBairro(null); buscar(criterio); }} buscando={buscando} />

      {(buscando || resultado) && <SourceProgress fontes={resultado?.sources} buscando={buscando} />}

      {erro && <p className="recado">{erro}</p>}

      {resultado?.status === 'ERRO' && (
        <p className="recado">
          Nenhuma combinação encontrada
          {degradadas.length > 0 ? `. Fontes com problema: ${degradadas.join(', ')}.` : ' para essas datas.'}
        </p>
      )}

      {resultado?.status === 'PARCIAL' && (
        <p className="recado">Resultado parcial — sem dados de: {degradadas.join(', ')}.</p>
      )}

      {resultado && resultado.status !== 'ERRO' && (
        <>
          <NeighborhoodChips
            bairros={resultado.neighborhoods}
            selecionado={bairro}
            onSelecionar={(novo) => aplicar(filtros, novo)}
          />
          <FilterBar filtros={filtros} onMudar={(novos) => aplicar(novos)} />

          {resultado.packages.length === 0 && !buscando && (
            <p className="recado">
              {bairro
                ? `Nenhum pacote em ${bairro} com esses filtros. Toque em "Todos os bairros" ou afrouxe algum filtro.`
                : 'Nenhum pacote passa nos filtros. Afrouxe algum deles.'}
            </p>
          )}

          <section className="pacotes">
            {resultado.packages.map((pacote, indice) => (
              <PackageCard key={pacote.id} pacote={pacote} destaque={indice === 0} />
            ))}
          </section>

          <DateOptions opcoes={resultado.dateOptions} />
        </>
      )}
    </main>
  );
}
