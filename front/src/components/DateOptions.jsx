import { brl } from '../formato';

export function DateOptions({ opcoes }) {
  if (!opcoes || opcoes.length === 0) return null;
  return (
    <section className="datas">
      <h2>Mudando a data</h2>
      <table>
        <thead>
          <tr><th>Ida</th><th>Volta</th><th>Custo</th><th>Economia</th></tr>
        </thead>
        <tbody>
          {opcoes.map((opcao) => (
            <tr key={opcao.departureDate}>
              <td>{opcao.departureDate}</td>
              <td>{opcao.returnDate}</td>
              <td>{brl(opcao.total)}</td>
              <td>{Number(opcao.difference) > 0 ? `−${brl(opcao.difference)}` : '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
