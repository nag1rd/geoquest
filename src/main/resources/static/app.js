const $ = selector => document.querySelector(selector);
let player;
try { player = localStorage.getItem('geoquest-player'); if (!player) { player = crypto.randomUUID(); localStorage.setItem('geoquest-player', player); } }
catch { player = crypto.randomUUID(); }
let countries = [], mode = 'capitals', question = null, score = 0, busy = false;
const escapeHtml = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
async function api(path, body) {
  const response = await fetch('/api' + path, {method: body === undefined ? 'GET' : 'POST', headers: {'Content-Type':'application/json','X-Player-Id':player}, ...(body === undefined ? {} : {body:JSON.stringify(body)})});
  if (!response.ok) { const error = await response.json().catch(() => ({})); throw new Error(error.message || 'Не удалось выполнить запрос. Попробуйте ещё раз.'); }
  return response.json();
}
function report(error) { $('#error').textContent = error.message || 'Сервер недоступен. Проверьте подключение.'; $('#error').hidden = false; }
function clearError() { $('#error').hidden = true; }
function showPage(page) {
  document.querySelectorAll('.page').forEach(el => el.hidden = el.id !== page);
  document.querySelectorAll('.nav').forEach(el => el.classList.toggle('active', el.dataset.page === page));
  clearError();
  if (page === 'stats') loadStats().catch(report);
}
document.querySelectorAll('.nav').forEach(button => button.onclick = () => showPage(button.dataset.page));
document.querySelectorAll('.mode').forEach(button => button.onclick = () => {
  mode = button.dataset.mode;
  document.querySelectorAll('.mode').forEach(el => { const active = el === button; el.classList.toggle('selected', active); el.setAttribute('aria-pressed', String(active)); });
});
function showSetup() { $('#setup').hidden = false; $('#game').hidden = true; $('#result').hidden = true; question = null; }
function renderQuestion() {
  $('#setup').hidden = true; $('#result').hidden = true; $('#game').hidden = false;
  $('#game').innerHTML = `<div class="quiz-card"><div class="quiz-top"><span>ВОПРОС ${question.number} ИЗ ${question.total}</span><span>${score * 10} XP в раунде</span></div><progress value="${question.number - 1}" max="${question.total}" aria-label="Прогресс раунда"></progress>${question.flag ? `<div class="flag" role="img" aria-label="Флаг для угадывания">${escapeHtml(question.flag)}</div>` : ''}<h2 tabindex="-1">${escapeHtml(question.prompt)}</h2><div class="answers">${question.options.map((option, i) => `<button class="answer" data-index="${i}">${escapeHtml(option)}</button>`).join('')}</div><div id="feedback" aria-live="polite"></div><button class="secondary" id="quit">← К выбору маршрута</button></div>`;
  $('#game h2').focus();
  document.querySelectorAll('.answer').forEach(button => button.onclick = () => submit(question.options[Number(button.dataset.index)]));
  $('#quit').onclick = () => { if (confirm('Выйти из раунда? Незавершённый результат не сохранится.')) showSetup(); };
}
$('#start').onclick = async () => {
  if (busy) return; busy = true; $('#start').disabled = true; clearError();
  try { question = await api('/quiz', {mode, region:$('#region').value}); score = 0; renderQuestion(); }
  catch (error) { report(error); }
  finally { busy = false; $('#start').disabled = false; }
};
async function submit(choice) {
  if (busy) return; busy = true; clearError();
  const buttons = [...document.querySelectorAll('.answer')]; buttons.forEach(el => el.disabled = true);
  try {
    const feedback = await api('/quiz/answer', {questionId:question.id, choice}); score = feedback.score;
    buttons.forEach(el => { const option = question.options[Number(el.dataset.index)]; if (option === feedback.answer) el.classList.add('correct'); else if (option === choice) el.classList.add('wrong'); });
    $('#feedback').innerHTML = `<div class="feedback"><strong>${feedback.correct ? 'Верно! +10 XP' : `Правильный ответ: ${escapeHtml(feedback.answer)}`}</strong><br>${escapeHtml(feedback.explanation)}</div><button class="primary" id="next">${feedback.finished ? 'Посмотреть результат' : 'Следующий вопрос →'}</button>`;
    $('#next').focus();
    $('#next').onclick = async () => {
      $('#next').disabled = true; clearError();
      try {
        if (feedback.finished) { renderResult(); await loadStats(); }
        else { const state = await api('/quiz'); if (!state.question) throw new Error('Сессия закончилась. Начните новый раунд.'); question = state.question; renderQuestion(); }
      } catch (error) { report(error); if ($('#next')) $('#next').disabled = false; }
    };
  } catch (error) { report(error); buttons.forEach(el => el.disabled = false); }
  finally { busy = false; }
}
function renderResult() {
  $('#game').hidden = true; $('#result').hidden = false;
  $('#result').innerHTML = `<div class="result-card"><p class="eyebrow">МАРШРУТ ПРОЙДЕН</p><h2>Ещё немного мира в копилке</h2><div class="result-score">${score}<small> / ${question.total}</small></div><p>Правильных ответов · +${score * 10} XP<br>${score === question.total ? 'Идеальный раунд! Попробуй другой регион.' : 'Повтори страны в атласе и попробуй снова.'}</p><div class="actions"><button class="primary" id="again">Новый маршрут →</button><button class="secondary" id="review">Открыть атлас</button></div></div>`;
  $('#again').onclick = showSetup; $('#review').onclick = () => { showSetup(); showPage('catalog'); };
}
function renderCountries() {
  const query = $('#search').value.toLocaleLowerCase('ru').trim();
  const filtered = countries.filter(c => `${c.name} ${c.capital} ${c.region}`.toLocaleLowerCase('ru').includes(query));
  $('#countries').innerHTML = filtered.length ? filtered.map(c => `<article class="country"><span class="emoji" role="img" aria-label="Флаг: ${escapeHtml(c.name)}">${c.flag}</span><h3>${escapeHtml(c.name)}</h3><p>${escapeHtml(c.capital)}</p><p>${escapeHtml(c.region)}</p></article>`).join('') : '<p class="empty">Ничего не найдено. Попробуй другое название.</p>';
}
$('#search').oninput = renderCountries;
async function loadStats() {
  const stats = await api('/stats'), s = stats.summary, xp = Number(s.CORRECT) * 10;
  $('#level').textContent = `Уровень ${Math.floor(xp / 100) + 1} · ${xp} XP`;
  $('#stats-content').innerHTML = `<div class="metrics"><div class="metric"><strong>${s.GAMES}</strong><span>раундов пройдено</span></div><div class="metric"><strong>${s.TOTAL ? Math.round(s.CORRECT / s.TOTAL * 100) : 0}%</strong><span>точность ответов</span></div><div class="metric"><strong>${xp}</strong><span>очков опыта</span></div></div><h2>Последние путешествия</h2>${stats.history.length ? stats.history.map(row => `<div class="history-row"><span>${row.MODE === 'flags' ? '⚑ Флаги' : '⌂ Столицы'}</span><span>${new Date(row.FINISHED_AT).toLocaleDateString('ru-RU')}</span><strong>${row.CORRECT} / ${row.TOTAL} · +${row.CORRECT * 10} XP</strong></div>`).join('') : '<p class="empty">Первое путешествие ещё впереди. Пройди раунд — здесь появится результат.</p>'}<p class="note">Прогресс связан с этим браузером. Каждый уровень — 100 XP.</p>`;
}
async function init() {
  try {
    countries = await api('/countries');
    [...new Set(countries.map(c => c.region))].forEach(region => { const option = document.createElement('option'); option.value = region; option.textContent = region; $('#region').append(option); });
    renderCountries(); await loadStats();
    const state = await api('/quiz'); if (state.question) { question = state.question; score = state.score; renderQuestion(); }
  } catch (error) { report(error); }
}
init();
