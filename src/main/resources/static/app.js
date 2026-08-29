const form = document.querySelector('#search-form');
const progress = document.querySelector('#progress');
const sourcesList = document.querySelector('#sources');
const filters = document.querySelector('#filters');
const message = document.querySelector('#message');
const packagesEl = document.querySelector('#packages');
const dateOptionsEl = document.querySelector('#date-options');
const neighborhoodsEl = document.querySelector('#neighborhoods');
const chipsEl = document.querySelector('#neighborhood-chips');
const destinationLabel = document.querySelector('#destination-label');
const dateTable = document.querySelector('#date-table');
const demoBanner = document.querySelector('#demo-banner');

const LABELS = {
  BEST_VALUE_OVERALL: 'Pacote Ideal',
  SMART_BUDGET: 'Econômico Inteligente',
  MAX_COMFORT: 'Máximo Conforto'
};

const brl = (value) => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
const inDays = (days) => new Date(Date.now() + days * 86400000).toISOString().slice(0, 10);

document.querySelector('input[name="departureDate"]').value = inDays(45);
document.querySelector('input[name="returnDate"]').value = inDays(48);

let searchId = null;
let neighborhood = null;

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  const data = new FormData(form);
  const body = {
    origin: data.get('origin'),
    destination: data.get('destination'),
    departureDate: data.get('departureDate'),
    returnDate: data.get('returnDate'),
    travelers: Number(data.get('travelers')),
    checkedBagRequested: data.get('checkedBagRequested') === 'on',
    carRequired: data.get('carRequired') === 'on',
    flexibleDates: data.get('flexibleDates') === 'on'
  };

  reset();
  destinationLabel.textContent = body.destination;
  progress.hidden = false;

  const started = await fetch('/api/search', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });

  if (!started.ok) {
    show('Não foi possível iniciar a busca. Confira os campos e tente de novo.');
    progress.hidden = true;
    return;
  }

  searchId = (await started.json()).searchId;
  poll();
});

filters.addEventListener('change', () => { if (searchId) load(); });

function reset() {
  packagesEl.innerHTML = '';
  sourcesList.innerHTML = '';
  chipsEl.innerHTML = '';
  neighborhood = null;
  dateOptionsEl.hidden = true;
  neighborhoodsEl.hidden = true;
  filters.hidden = true;
  message.hidden = true;
}

function show(text) {
  message.textContent = text;
  message.hidden = false;
}

async function poll(attempt = 0) {
  const result = await load();
  if (result && result.status === 'BUSCANDO' && attempt < 60) {
    setTimeout(() => poll(attempt + 1), 1000);
  }
}

function filterQuery() {
  const params = new URLSearchParams();
  const maxPrice = document.querySelector('#f-maxPrice').value;
  const minRating = document.querySelector('#f-minRating').value;
  if (maxPrice) params.set('maxPrice', maxPrice);
  if (minRating) params.set('minRating', minRating);
  if (document.querySelector('#f-directFlightOnly').checked) params.set('directFlightOnly', 'true');
  if (document.querySelector('#f-breakfastIncluded').checked) params.set('breakfastIncluded', 'true');
  if (document.querySelector('#f-freeCancellation').checked) params.set('freeCancellation', 'true');
  if (neighborhood) params.set('neighborhood', neighborhood);
  return params.toString() ? `?${params}` : '';
}

async function load() {
  const response = await fetch(`/api/search/${searchId}${filterQuery()}`);
  if (!response.ok) {
    show('Essa busca expirou. Faça uma nova.');
    return null;
  }
  const result = await response.json();
  render(result);
  return result;
}

function render(result) {
  demoBanner.hidden = !result.demo;
  renderSources(result.sources);

  if (result.status === 'BUSCANDO') return;
  progress.hidden = true;

  if (result.status === 'ERRO') {
    const broken = result.sources.filter((s) => s.health === 'DEGRADADO').map((s) => s.source);
    show(broken.length
      ? `Nenhuma combinação encontrada. Fontes com problema: ${broken.join(', ')}.`
      : 'Nenhuma combinação encontrada para essas datas.');
    return;
  }

  filters.hidden = false;
  renderNeighborhoods(result.neighborhoods);

  if (result.status === 'PARCIAL') {
    const broken = result.sources.filter((s) => s.health === 'DEGRADADO').map((s) => s.source);
    show(`Resultado parcial — sem dados de: ${broken.join(', ')}.`);
  } else if (result.packages.length === 0) {
    show(neighborhood
      ? `Nenhum pacote em ${neighborhood} com esses filtros. Toque em "Todos os bairros" ou afrouxe algum filtro.`
      : 'Nenhum pacote passa nos filtros. Afrouxe algum deles.');
  }

  renderPackages(result.packages);
  renderDateOptions(result.dateOptions);
}

function renderSources(sources) {
  sourcesList.innerHTML = sources.map((source) => `
    <li class="source ${source.health.toLowerCase()}">
      <b>${source.source}</b>
      <span>${source.health === 'OK' ? `${source.offers} ofertas` : source.message || 'sem resposta'}</span>
    </li>`).join('');
}

function renderPackages(packages) {
  packagesEl.innerHTML = packages.map((item, index) => `
    <article class="package ${index === 0 ? 'featured' : ''}">
      <header>
        <span class="pill">${LABELS[item.recommendationType] || item.recommendationType}</span>
        <div class="price">
          <strong>${brl(item.realCost)}</strong>
          ${Number(item.hiddenCosts) > 0 ? `<s>${brl(item.advertisedPrice)}</s>` : ''}
        </div>
      </header>
      ${Number(item.hiddenCosts) > 0 ? `<p class="hidden-costs">${brl(item.hiddenCosts)} em custos que o anúncio não mostra</p>` : ''}
      <dl class="details">
        <div><dt>Hospedagem</dt><dd>${item.lodgingName} — ${item.neighborhood} — nota ${item.lodgingRating} (${item.lodgingReviews} avaliações)</dd></div>
        <div><dt>Voo</dt><dd>${item.airline} — ${item.stops === 0 ? 'direto' : `${item.stops} parada(s)`}</dd></div>
        ${item.carSupplier ? `<div><dt>Carro</dt><dd>${item.carSupplier} — ${item.carCategory}</dd></div>` : ''}
        <div><dt>Incluído</dt><dd>${item.included.join(', ') || '—'}</dd></div>
      </dl>
      ${item.why ? `<p class="why"><b>Por que este pacote:</b> ${item.why}</p>` : ''}
      <footer>
        ${item.links.map((link) => `<a class="book" href="${link.url}" target="_blank" rel="noopener">Reservar no ${link.partner}</a>`).join('')}
      </footer>
    </article>`).join('');
}

function renderNeighborhoods(areas) {
  if (!areas || areas.length === 0) { neighborhoodsEl.hidden = true; return; }
  neighborhoodsEl.hidden = false;
  const all = `<button class="chip ${neighborhood ? '' : 'active'}" data-area="">Todos os bairros</button>`;
  chipsEl.innerHTML = all + areas.map((area) => `
    <button class="chip ${neighborhood === area.neighborhood ? 'active' : ''}" data-area="${area.neighborhood}">
      ${area.neighborhood}
      <small>${area.packages} ${area.packages === 1 ? 'opção' : 'opções'} · a partir de ${brl(area.cheapest)}</small>
    </button>`).join('');
}

chipsEl.addEventListener('click', (event) => {
  const chip = event.target.closest('.chip');
  if (!chip || !searchId) return;
  neighborhood = chip.dataset.area || null;
  load();
});

function renderDateOptions(options) {
  if (!options || options.length === 0) { dateOptionsEl.hidden = true; return; }
  dateOptionsEl.hidden = false;
  dateTable.innerHTML = `
    <tr><th>Ida</th><th>Volta</th><th>Custo</th><th>Economia</th></tr>
    ${options.map((option) => `
      <tr>
        <td>${option.departureDate}</td>
        <td>${option.returnDate}</td>
        <td>${brl(option.total)}</td>
        <td>${Number(option.difference) > 0 ? `−${brl(option.difference)}` : '—'}</td>
      </tr>`).join('')}`;
}
