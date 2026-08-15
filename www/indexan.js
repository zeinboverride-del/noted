const semuaHari = document.querySelectorAll('.isi_hari');
const containerHari = document.querySelector('.hari');
const appHeader = document.querySelector('.app-header');
const allQuestsSection = document.getElementById('allQuestsSection');
const allQuestsList = document.getElementById('allQuestsList');
const todayBadge = document.getElementById('todayBadge');
const questInDay = document.querySelector('.questinday');
const btnBack = document.getElementById('btnBack');
const btnTambah = document.getElementById('button');
const btnClearCompleted = document.getElementById('btnClearCompleted');
const noteForm = document.getElementById('noteForm');
const btnCloseForm = document.getElementById('btnCloseForm');
const btnBatal = document.getElementById('btnBatal');
const btnSelesai = document.getElementById('end');
const inputJudul = document.getElementById('inputJudul');
const inputIsi = document.getElementById('inputIsi');
const namaHariEl = document.getElementById('namaHari');
const questDayStats = document.getElementById('questDayStats');
const notesList = document.getElementById('notesList');
const formTitle = document.getElementById('formTitle');
const totalQuestBadge = document.getElementById('totalQuestBadge');
const globalProgressBar = document.getElementById('globalProgressBar');
const globalProgressText = document.getElementById('globalProgressText');
const globalQuestPanel = document.getElementById('globalQuestPanel');
const specialFormWrap = document.getElementById('specialFormWrap');
const specialDateMode = document.getElementById('specialDateMode');
const specialMonthMode = document.getElementById('specialMonthMode');
const specialDaySelect = document.getElementById('specialDaySelect');
const specialMonthSelect = document.getElementById('specialMonthSelect');
const specialDateBlock = document.getElementById('specialDateBlock');
const specialMonthBlock = document.getElementById('specialMonthBlock');

const STORAGE_KEY = 'noted_catatan_harian';
const HARI_NAMES = ['Minggu', 'Senin', 'Selasa', 'Rabu', 'Kamis', 'Jumat', 'Sabtu'];
const SPECIAL_DAY_NAME = 'Khusus';

let hariAktif = '';
let editId = null;

function getDeviceDays() {
    const index = new Date().getDay();
    return {
        today: HARI_NAMES[index],
        yesterday: HARI_NAMES[(index + 6) % 7],
        tomorrow: HARI_NAMES[(index + 1) % 7]
    };
}

function loadNotes() {
    try {
        return JSON.parse(localStorage.getItem(STORAGE_KEY)) || {};
    } catch {
        return {};
    }
}

function saveNotes(data) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    syncUI();
}

function normalizeNotes(data) {
    let changed = false;

    for (const hari in data) {
        if (!Array.isArray(data[hari])) {
            delete data[hari];
            changed = true;
            continue;
        }

        data[hari] = data[hari].map(function(note, index) {
            const normalized = Object.assign({}, note);

            if (!normalized.id) {
                normalized.id = 'q-' + hari + '-' + index + '-' + Date.now();
                changed = true;
            }
            if (normalized.selesai === undefined) {
                normalized.selesai = false;
                changed = true;
            }

            return normalized;
        });
    }

    if (changed) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    }

    return data;
}

function getDayStats(allNotes, hari) {
    const quests = filterQuestListForDisplay(allNotes[hari] || [], hari);
    const total = quests.length;
    const completed = quests.filter(function(q) { return q.selesai; }).length;

    return { total, completed, active: total - completed };
}

function filterQuestListForDisplay(quests, hari) {
    if (hari !== SPECIAL_DAY_NAME) {
        return quests;
    }

    return (quests || []).filter(function(q) {
        return isSpecialQuestVisible(q);
    });
}

function getGlobalStats(allNotes) {
    let total = 0;
    let completed = 0;

    for (const hari in allNotes) {
        const stats = getDayStats(allNotes, hari);
        total += stats.total;
        completed += stats.completed;
    }

    return { total, completed, active: total - completed };
}

function flattenAllQuests(allNotes) {
    const flat = [];

    for (const hari in allNotes) {
        const visibleQuests = filterQuestListForDisplay(allNotes[hari] || [], hari);
        visibleQuests.forEach(function(quest) {
            flat.push(Object.assign({}, quest, { hari: hari }));
        });
    }

    return flat;
}

function sortQuests(quests, options) {
    const deviceDays = getDeviceDays();
    const active = quests.filter(function(q) { return !q.selesai; });
    const completed = quests.filter(function(q) { return q.selesai; });

    const byNewest = function(a, b) {
        return new Date(b.waktu) - new Date(a.waktu);
    };

    if (options && options.global) {
        const overdue = active.filter(function(q) { return q.hari === deviceDays.yesterday; });
        const otherActive = active.filter(function(q) { return q.hari !== deviceDays.yesterday; });

        overdue.sort(byNewest);
        otherActive.sort(byNewest);
        completed.sort(byNewest);

        return overdue.concat(otherActive, completed);
    }

    active.sort(byNewest);
    completed.sort(byNewest);

    return active.concat(completed);
}

function isQuestOverdue(quest) {
    const deviceDays = getDeviceDays();
    return !quest.selesai && quest.hari === deviceDays.yesterday;
}

function getStartOfDay(date) {
    return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function daysBetween(start, end) {
    const msPerDay = 24 * 60 * 60 * 1000;
    const a = getStartOfDay(start).getTime();
    const b = getStartOfDay(end).getTime();
    return Math.round((b - a) / msPerDay);
}

function isSpecialQuestVisible(quest) {
    if (!quest || !quest.specialMode) {
        return true;
    }

    const now = new Date();
    const nowYear = now.getFullYear();

    if (quest.specialMode === 'tanggal') {
        const selectedDay = Number(quest.specialDay || 1);
        const daysInMonth = new Date(nowYear, now.getMonth() + 1, 0).getDate();

        if (selectedDay < 1 || selectedDay > daysInMonth) {
            return false;
        }

        const target = new Date(nowYear, now.getMonth(), selectedDay);
        const diff = daysBetween(now, target);
        return diff >= 0 && diff <= 10;
    }

    if (quest.specialMode === 'bulan') {
        const selectedMonth = Number(quest.specialMonth || now.getMonth() + 1);
        const targetMonthIndex = selectedMonth - 1;

        if (targetMonthIndex < 0 || targetMonthIndex > 11) {
            return false;
        }

        const target = new Date(nowYear, targetMonthIndex, 1);
        const diff = daysBetween(now, target);
        return diff >= 0 && diff <= 10;
    }

    return true;
}

function getSpecialScheduleLabel(quest) {
    if (!quest || !quest.specialMode) {
        return '';
    }

    if (quest.specialMode === 'tanggal') {
        return 'Jadwal Tanggal ' + String(quest.specialDay || 1);
    }

    if (quest.specialMode === 'bulan') {
        const monthNames = ['Januari','Februari','Maret','April','Mei','Juni','Juli','Agustus','September','Oktober','November','Desember'];
        const month = monthNames[Number(quest.specialMonth || 1) - 1] || 'Bulan';
        return 'Jadwal Bulan ' + month;
    }

    return '';
}

function updateTotalQuestIndicator() {
    const allNotes = normalizeNotes(loadNotes());
    const global = getGlobalStats(allNotes);
    const percent = global.total > 0 ? Math.round((global.completed / global.total) * 100) : 0;

    totalQuestBadge.textContent = global.active > 0 ? String(global.active) : '0';
    globalProgressBar.style.width = percent + '%';
    globalProgressText.textContent = global.total > 0
        ? global.completed + ' / ' + global.total + ' cleared (' + percent + '%)'
        : 'Belum ada quest';

    globalQuestPanel.classList.toggle('all-clear', global.total > 0 && global.active === 0);
    totalQuestBadge.classList.toggle('pulse', global.active > 0);
}

function updateDayCards() {
    const allNotes = normalizeNotes(loadNotes());
    const deviceDays = getDeviceDays();

    todayBadge.textContent = 'Hari ini: ' + deviceDays.today;

    semuaHari.forEach(function(card) {
        const hari = card.dataset.hari;
        const stats = getDayStats(allNotes, hari);
        const badge = card.querySelector('.day-quest-badge');
        const fill = card.querySelector('.day-progress-fill');
        const percent = stats.total > 0 ? (stats.completed / stats.total) * 100 : 0;

        card.classList.remove(
            'has-active', 'all-done', 'empty',
            'is-today', 'is-tomorrow', 'is-yesterday',
            'today-clear', 'today-warning', 'yesterday-warning'
        );

        if (hari === deviceDays.today) {
            card.classList.add('is-today');
            if (stats.active > 0) {
                card.classList.add('today-warning');
            } else {
                card.classList.add('today-clear');
            }
        }

        if (hari === deviceDays.tomorrow) {
            card.classList.add('is-tomorrow');
        }

        if (hari === deviceDays.yesterday) {
            card.classList.add('is-yesterday');
            if (stats.active > 0) {
                card.classList.add('yesterday-warning');
            }
        }

        if (stats.total === 0) {
            badge.textContent = 'No Quest';
            card.classList.add('empty');
        } else if (stats.active === 0) {
            badge.textContent = stats.total + ' Cleared!';
            card.classList.add('all-done');
        } else {
            badge.textContent = stats.active + ' Active / ' + stats.total;
            card.classList.add('has-active');
        }

        fill.style.width = percent + '%';
    });
}

function updateQuestDayStats() {
    const allNotes = normalizeNotes(loadNotes());
    const stats = getDayStats(allNotes, hariAktif);

    if (stats.total === 0) {
        questDayStats.textContent = 'Belum ada quest';
        btnClearCompleted.disabled = true;
        return;
    }

    questDayStats.textContent = stats.completed + ' / ' + stats.total + ' quest selesai';
    btnClearCompleted.disabled = stats.completed === 0;
}

function syncUI() {
    updateTotalQuestIndicator();
    updateDayCards();
    renderAllQuests();

    if (hariAktif) {
        updateQuestDayStats();
        renderNotes();
    }
}

function formatWaktu(isoString) {
    const date = new Date(isoString);
    return date.toLocaleString('id-ID', {
        hour: '2-digit',
        minute: '2-digit',
        day: 'numeric',
        month: 'short'
    });
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function buildQuestCardHTML(catatan, options) {
    const showDay = options && options.showDay;
    const overdue = isQuestOverdue(catatan);
    const extraClass = (catatan.selesai ? ' completed' : '') + (overdue ? ' overdue' : '');
    const specialSchedule = getSpecialScheduleLabel(catatan);

    return (
        '<article class="note-card' + extraClass + '" data-id="' + catatan.id + '" data-hari="' + catatan.hari + '">' +
            '<div class="note-header-flex">' +
                '<button type="button" class="quest-toggle' + (catatan.selesai ? ' done' : '') + '" data-id="' + catatan.id + '" data-hari="' + catatan.hari + '" aria-label="Tandai quest selesai">' +
                    '<span class="quest-toggle-ring"></span>' +
                    '<span class="quest-toggle-glow"></span>' +
                    '<i class="fa-solid fa-scroll quest-icon-pending"></i>' +
                    '<i class="fa-solid fa-trophy quest-icon-done"></i>' +
                '</button>' +
                '<div class="note-content-flex">' +
                    (showDay ? '<span class="quest-day-label">' + escapeHtml(catatan.hari) + '</span>' : '') +
                    (overdue ? '<span class="overdue-tag"><i class="fa-solid fa-fire"></i> OVERDUE</span>' : '') +
                    '<div class="quest-status-tag' + (catatan.selesai ? ' cleared' : '') + '">' +
                        (catatan.selesai ? 'QUEST CLEAR' : 'ACTIVE') +
                    '</div>' +
                    '<h4>' + escapeHtml(catatan.judul) + '</h4>' +
                    '<p>' + escapeHtml(catatan.isi) + '</p>' +
                    (specialSchedule ? '<span class="special-schedule-label">' + escapeHtml(specialSchedule) + '</span>' : '') +
                    '<span class="note-time"><i class="fa-regular fa-clock"></i> ' + formatWaktu(catatan.waktu) + '</span>' +
                    '<div class="note-actions">' +
                        '<button type="button" class="action-btn edit" data-id="' + catatan.id + '" data-hari="' + catatan.hari + '"><i class="fa-solid fa-pen"></i> Edit</button>' +
                        '<button type="button" class="action-btn delete" data-id="' + catatan.id + '" data-hari="' + catatan.hari + '"><i class="fa-solid fa-trash"></i> Hapus</button>' +
                    '</div>' +
                '</div>' +
            '</div>' +
        '</article>'
    );
}

function renderAllQuests() {
    const allNotes = normalizeNotes(loadNotes());
    const flat = flattenAllQuests(allNotes);
    const sorted = sortQuests(flat, { global: true });

    allQuestsList.innerHTML = '';

    if (sorted.length === 0) {
        allQuestsList.innerHTML = '<p class="notes-empty"><i class="fa-solid fa-scroll"></i> Belum ada quest di board ini.</p>';
        return;
    }

    sorted.forEach(function(catatan) {
        allQuestsList.insertAdjacentHTML('beforeend', buildQuestCardHTML(catatan, { showDay: true }));
    });
}

function renderNotes() {
    const allNotes = normalizeNotes(loadNotes());
    const baseList = (allNotes[hariAktif] || []).map(function(q) {
        return Object.assign({}, q, { hari: hariAktif });
    });

    const catatanHari = (hariAktif === SPECIAL_DAY_NAME)
    ? sortQuests(baseList)
    : sortQuests(filterQuestListForDisplay(baseList, hariAktif));

    notesList.innerHTML = '';
    updateQuestDayStats();

    if (catatanHari.length === 0) {
        const emptyText = hariAktif === SPECIAL_DAY_NAME
            ? 'Belum ada quest khusus yang bisa ditampilkan. Tunggu 10 hari menjelang jadwal.'
            : 'Belum ada quest. Tekan <strong>New Quest</strong> untuk mulai!';
        notesList.innerHTML = '<p class="notes-empty"><i class="fa-solid fa-map"></i> ' + emptyText + '</p>';
        return;
    }

    catatanHari.forEach(function(catatan) {
        notesList.insertAdjacentHTML('beforeend', buildQuestCardHTML(catatan, { showDay: false }));
    });
}

function tampilkanHari(hari) {
    hariAktif = hari;
    namaHariEl.textContent = hari;
    containerHari.style.display = 'none';
    allQuestsSection.style.display = 'none';
    appHeader.style.display = 'none';
    questInDay.classList.add('active');
    tutupForm();
    renderNotes();
    history.pushState({ view: 'questinday' }, '');
}

function kembaliKeAwal() {
    questInDay.classList.remove('active');
    containerHari.style.display = 'grid';
    allQuestsSection.style.display = 'block';
    appHeader.style.display = 'block';
    hariAktif = '';
    tutupForm();
    syncUI();
}

function bukaForm(mode, id) {
    btnTambah.classList.add('hidden');
    noteForm.classList.add('active');
    editId = id || null;

    if (hariAktif === SPECIAL_DAY_NAME) {
        specialFormWrap.classList.add('active');
        specialFormWrap.style.display = 'block';
    } else {
        specialFormWrap.classList.remove('active');
        specialFormWrap.style.display = 'none';
    }

    if (mode === 'edit') {
        formTitle.textContent = 'Edit Quest';
    } else {
        formTitle.textContent = 'Quest Baru';
        inputJudul.value = '';
        inputIsi.value = '';
        resetSpecialFormDefaults();
    }

    inputJudul.focus();
    history.pushState({ view: 'noteForm' }, '');
}

function resetSpecialFormDefaults() {
    specialDateMode.checked = true;
    specialMonthMode.checked = false;
    specialDaySelect.value = String(new Date().getDate());
    specialMonthSelect.value = String(new Date().getMonth() + 1);
    toggleSpecialSelections();
}

function toggleSpecialSelections() {
    if (specialDateMode.checked) {
        specialDateBlock.style.display = 'block';
        specialMonthBlock.style.display = 'none';
    } else {
        specialDateBlock.style.display = 'none';
        specialMonthBlock.style.display = 'block';
        specialDaySelect.value = '1';
    }
}

function tutupForm() {
    noteForm.classList.remove('active');
    btnTambah.classList.remove('hidden');
    inputJudul.value = '';
    inputIsi.value = '';
    specialFormWrap.classList.remove('active');
    specialFormWrap.style.display = 'none';
    editId = null;
}

function simpanCatatan() {
    const judul = inputJudul.value.trim();
    const isi = inputIsi.value.trim();

    if (!judul && !isi) {
        inputJudul.focus();
        return;
    }

    const allNotes = normalizeNotes(loadNotes());
    if (!allNotes[hariAktif]) {
        allNotes[hariAktif] = [];
    }

    const specialPayload = hariAktif === SPECIAL_DAY_NAME
        ? {
            specialMode: specialDateMode.checked ? 'tanggal' : 'bulan',
            specialDay: specialDateMode.checked ? Number(specialDaySelect.value) : 1,
            specialMonth: specialMonthMode.checked ? Number(specialMonthSelect.value) : Number(new Date().getMonth() + 1)
        }
        : null;

    if (editId) {
        const index = allNotes[hariAktif].findIndex(function(q) { return q.id === editId; });
        if (index > -1) {
            allNotes[hariAktif][index].judul = judul || 'Tanpa judul';
            allNotes[hariAktif][index].isi = isi;

            if (hariAktif === SPECIAL_DAY_NAME && specialPayload) {
                allNotes[hariAktif][index].specialMode = specialPayload.specialMode;
                allNotes[hariAktif][index].specialDay = specialPayload.specialDay;
                allNotes[hariAktif][index].specialMonth = specialPayload.specialMonth;
            }
        }
    } else {
        allNotes[hariAktif].push({
            id: 'q-' + Date.now() + '-' + Math.random().toString(36).slice(2, 7),
            judul: judul || 'Tanpa judul',
            isi: isi,
            waktu: new Date().toISOString(),
            selesai: false,
            ...(specialPayload || {})
        });
    }

    saveNotes(allNotes);
    tutupForm();
    renderNotes();
}

function toggleQuestSelesai(id, hari) {
    const targetHari = hari || hariAktif;
    const allNotes = normalizeNotes(loadNotes());
    if (!allNotes[targetHari]) return;

    const index = allNotes[targetHari].findIndex(function(q) { return q.id === id; });
    if (index === -1) return;

    allNotes[targetHari][index].selesai = !allNotes[targetHari][index].selesai;
    saveNotes(allNotes);
}

function hapusCatatan(id, hari) {
    const targetHari = hari || hariAktif;
    const allNotes = normalizeNotes(loadNotes());
    if (!allNotes[targetHari]) return;

    allNotes[targetHari] = allNotes[targetHari].filter(function(q) { return q.id !== id; });

    if (allNotes[targetHari].length === 0) {
        delete allNotes[targetHari];
    }

    saveNotes(allNotes);
}

function hapusSemuaSelesai() {
    const allNotes = normalizeNotes(loadNotes());
    if (!allNotes[hariAktif]) return;

    const sebelum = allNotes[hariAktif].length;
    allNotes[hariAktif] = allNotes[hariAktif].filter(function(q) { return !q.selesai; });

    if (allNotes[hariAktif].length === sebelum) return;

    if (allNotes[hariAktif].length === 0) {
        delete allNotes[hariAktif];
    }

    saveNotes(allNotes);
}

function handleQuestListClick(e, contextHari) {
    const toggleBtn = e.target.closest('.quest-toggle');
    if (toggleBtn) {
        toggleQuestSelesai(toggleBtn.dataset.id, toggleBtn.dataset.hari || contextHari);
        return;
    }

    const deleteBtn = e.target.closest('.delete');
    if (deleteBtn) {
        hapusCatatan(deleteBtn.dataset.id, deleteBtn.dataset.hari || contextHari);
        return;
    }

    const editBtn = e.target.closest('.edit');
    if (editBtn) {
        const targetHari = editBtn.dataset.hari || contextHari;
        const allNotes = normalizeNotes(loadNotes());
        const targetQuest = (allNotes[targetHari] || []).find(function(q) { return q.id === editBtn.dataset.id; });

        if (targetQuest) {
            if (targetHari !== hariAktif) {
                tampilkanHari(targetHari);
            }
            bukaForm('edit', editBtn.dataset.id);
            inputJudul.value = targetQuest.judul;
            inputIsi.value = targetQuest.isi;

            if (targetHari === SPECIAL_DAY_NAME && targetQuest.specialMode) {
                if (targetQuest.specialMode === 'tanggal') {
                    specialDateMode.checked = true;
                    specialMonthMode.checked = false;
                    specialDaySelect.value = String(targetQuest.specialDay || 1);
                } else {
                    specialDateMode.checked = false;
                    specialMonthMode.checked = true;
                    specialMonthSelect.value = String(targetQuest.specialMonth || new Date().getMonth() + 1);
                    specialDaySelect.value = '1';
                }
                toggleSpecialSelections();
            }
        }
    }
}

notesList.addEventListener('click', function(e) {
    handleQuestListClick(e, hariAktif);
});

allQuestsList.addEventListener('click', function(e) {
    handleQuestListClick(e, null);
});

semuaHari.forEach(function(hari) {
    hari.addEventListener('click', function() {
        tampilkanHari(hari.dataset.hari);
    });
});

specialDateMode.addEventListener('change', toggleSpecialSelections);
specialMonthMode.addEventListener('change', toggleSpecialSelections);

btnBack.addEventListener('click', kembaliKeAwal);
btnTambah.addEventListener('click', function() { bukaForm('tambah'); });
btnClearCompleted.addEventListener('click', hapusSemuaSelesai);
btnCloseForm.addEventListener('click', tutupForm);
btnBatal.addEventListener('click', tutupForm);
btnSelesai.addEventListener('click', simpanCatatan);

inputJudul.addEventListener('keydown', function(e) {
    if (e.key === 'Enter') {
        e.preventDefault();
        inputIsi.focus();
    }
});

normalizeNotes(loadNotes());
syncUI();

if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('./sw.js')
            .then(registration => {
                console.log('ServiceWorker sukses didaftarkan dengan scope: ', registration.scope);
            })
            .catch(error => {
                console.log('ServiceWorker gagal didaftarkan: ', error);
            });
    });
}

function checkAndSendNotifications() {
    const now = new Date();
    const hours = now.getHours();
    const minutes = now.getMinutes();

    // Jalankan hanya di menit ke-0 (06:00, 14:00, 18:00)
    if (minutes !== 0) return;

    const allNotes = normalizeNotes(loadNotes());
    const deviceDays = getDeviceDays();

    const todayQuests = allNotes[deviceDays.today] || [];
    const activeToday = todayQuests.filter(q => !q.selesai);

    const tomorrowQuests = allNotes[deviceDays.tomorrow] || [];
    const activeTomorrow = tomorrowQuests.filter(q => !q.selesai);

    // 1. JAM 06.00 PAGI: Khusus Misi Hari Ini
    if (hours === 6) {
        if (activeToday.length > 0) {
            sendQuestNotification(
                'Jangan lupa Hari Ini!',
                `Ada ${activeToday.length} quest aktif buat hari ini. Cek dulu sebelum berangkat!`
            );
        }
    }

    // 2. JAM 14.00 SIANG (PULANG SEKOLAH): Langsung Intip Misi Besok!
    if (hours === 14) {
        if (activeTomorrow.length > 0) {
            sendQuestNotification(
                'Persiapan Buat Besok!',
                `Besok kamu punya ${activeTomorrow.length} quest. Lebih baik dikerjakan terlebih dahulu!`
            );
        }
    }

    // 3. JAM 18.00 SORE: Evaluasi Malam & Last Call
    if (hours === 18) {
        if (activeToday.length > 0) {
            sendQuestNotification(
                'Tugas Hari Ini Belum Kelar!',
                `Masih ada ${activeToday.length} quest hari ini yang belum selesai. Jangan sampai jadi Overdue!`
            );
        } else if (activeTomorrow.length > 0) {
            sendQuestNotification(
                'Tugas Besok Sudah Siap?',
                `Hari ini CLEAR! Jangan lupa besok ada ${activeTomorrow.length} quest yang menanti.`
            );
        }
    }
}
window.addEventListener('popstate', function(e) {
    // Jika Form Quest sedang terbuka, tutup formnya dulu
    if (noteForm.classList.contains('active')) {
        tutupForm();
        return;
    }

    // Jika sedang berada di tampilan Hari/Quest, kembali ke Menu Utama
    if (questInDay.classList.contains('active')) {
        kembaliKeAwal();
        return;
    }
});