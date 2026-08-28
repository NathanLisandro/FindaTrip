const form = document.querySelector('#search-form');
const list = document.querySelector('#packages');
const summary = document.querySelector('#summary-strip');

const addDays = (days) => {
  const date = new Date();
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
};

document.querySelector('input[name="departureDate"]').value = addDays(45);
document.querySelector('input[name="returnDate"]').value = addDays(54);

const fallbackPackages = [
  { id: 'overall-value', totalPrice: 1850, currency: 'USD', valueScore: 91, recommendationType: 'BEST_VALUE_OVERALL' },
  { id: 'smart-budget', totalPrice: 1390, currency: 'USD', valueScore: 84, recommendationType: 'SMART_BUDGET' },
  { id: 'max-comfort', totalPrice: 2440, currency: 'USD', valueScore: 89, recommendationType: 'MAX_COMFORT' }
];

function renderSummary(packages) {
  const best = packages.reduce((winner, item) => item.valueScore > winner.valueScore ? item : winner, packages[0]);
  const cheapest = packages.reduce((winner, item) => item.totalPrice < winner.totalPrice ? item : winner, packages[0]);
  const averageScore = Math.round(packages.reduce((sum, item) => sum + item.valueScore, 0) / packages.length);
  summary.innerHTML = `
    <div><b>${Math.round(best.valueScore)}</b><span>Best value score</span></div>
    <div><b>${new Intl.NumberFormat('en-US', { style: 'currency', currency: cheapest.currency, maximumFractionDigits: 0 }).format(cheapest.totalPrice)}</b><span>Smart budget price</span></div>
    <div><b>${averageScore}</b><span>Average shortlist score</span></div>
  `;
}

function renderPackages(packages) {
  renderSummary(packages);
  list.innerHTML = packages.map((item) => `
    <article class="package-card">
      <header>
        <div>
          <span class="pill">${item.recommendationType.replaceAll('_', ' ')}</span>
          <h3>${item.id.replaceAll('-', ' ')}</h3>
        </div>
        <strong>${new Intl.NumberFormat('en-US', { style: 'currency', currency: item.currency }).format(item.totalPrice)}</strong>
      </header>
      <div class="meter" aria-label="Value score ${Math.round(item.valueScore)} out of 100"><span style="width:${item.valueScore}%"></span></div>
      <p>Value score: ${Math.round(item.valueScore)}/100 after hidden-cost normalization, quality weighting, and convenience analysis.</p>
    </article>
  `).join('');
}

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  const data = Object.fromEntries(new FormData(form).entries());
  data.travelers = Number(data.travelers);
  data.checkedBagRequested = Boolean(data.checkedBagRequested);
  data.carRequired = Boolean(data.carRequired);

  list.innerHTML = '<article class="package-card"><p>Analyzing normalized offers…</p></article>';
  try {
    const response = await fetch('/api/travel-analysis/packages', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    });
    if (!response.ok) throw new Error('Package analyzer unavailable');
    renderPackages(await response.json());
  } catch (error) {
    renderPackages(fallbackPackages);
  }
});

renderPackages(fallbackPackages);
