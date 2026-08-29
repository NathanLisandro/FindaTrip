import { useState } from 'react';
import { useSearch } from './hooks/useSearch';
import { SearchForm } from './components/SearchForm';
import { SourceProgress } from './components/SourceProgress';
import { DemoNotice } from './components/DemoNotice';
import { FilterSidebar } from './components/FilterSidebar';
import { PackageCard } from './components/PackageCard';
import { DateOptions } from './components/DateOptions';
import { inteiro } from './formato';

export default function App() {
  const { resultado, buscando, erro, buscar, filtrar } = useSearch();
  const [filtros, setFiltros] = useState({});
  const [bairro, setBairro] = useState(null);
  const [tipos, setTipos] = useState([]);
  const [mostrando, setMostrando] = useState(8);

  const aplicar = (novosFiltros, novoBairro, novosTipos) => {
    setFiltros(novosFiltros);
    setBairro(novoBairro);
    setTipos(novosTipos);
    setMostrando(8);
    filtrar({ ...novosFiltros, neighborhood: novoBairro || '', stayTypes: novosTipos });
  };

  const degradadas = (resultado?.sources || [])
    .filter((f) => f.health === 'DEGRADADO')
    .map((f) => f.source);

  const pacotes = resultado?.packages || [];
  const temResultado = resultado && resultado.status !== 'ERRO' && resultado.status !== 'BUSCANDO';

  return (
    <>
      <div className="topo">
        <div className="topo__interno">
          <span className="marca">FindaTrip</span>
          <span className="marca__lema">o custo real da viagem</span>
        </div>
      </div>

      <main className="tela">
        <header className="cabecalho">
          <h1>Para onde e quando?</h1>
          <p>Compara voo e hospedagem em vários sites e soma taxa, bagagem e seguro antes de comparar.</p>
        </header>

        <DemoNotice fontes={resultado?.sources} />

        <SearchForm
          onBuscar={(criterio) => { setFiltros({}); setBairro(null); setTipos([]); setMostrando(8); buscar(criterio); }}
          buscando={buscando}
        />

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

        {temResultado && (
          <div className="conteudo">
            <FilterSidebar
              bairros={resultado.neighborhoods}
              tipos={resultado.stayTypes}
              filtros={filtros}
              bairro={bairro}
              tiposEscolhidos={tipos}
              onFiltros={(novos) => aplicar(novos, bairro, tipos)}
              onBairro={(nova) => aplicar(filtros, nova, tipos)}
              onTipos={(novos) => aplicar(filtros, bairro, novos)}
            />

            <section className="resultados">
              <div className="resultados__topo">
                <strong>{inteiro(resultado.totalMatching || pacotes.length)} pacotes</strong>
                <span>ordenados por custo-benefício</span>
              </div>

              {pacotes.length === 0 && !buscando && (
                <p className="recado">
                  Nenhum pacote com esses filtros. Toque em Limpar ou afrouxe algum deles.
                </p>
              )}

              <div className="pacotes">
                {pacotes.slice(0, mostrando).map((pacote, indice) => (
                  <PackageCard key={pacote.id} pacote={pacote} destaque={indice === 0} />
                ))}
              </div>

              {pacotes.length > mostrando && (
                <button type="button" className="mais" onClick={() => setMostrando((m) => m + 8)}>
                  Ver mais {Math.min(8, pacotes.length - mostrando)} de {inteiro(pacotes.length)}
                </button>
              )}

              <DateOptions opcoes={resultado.dateOptions} />
            </section>
          </div>
        )}
      </main>
    </>
  );
}
