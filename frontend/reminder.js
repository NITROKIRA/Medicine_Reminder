(function() {
    if (!('Notification' in window)) return;

    let notifiedIds = new Set();
    let audioCtx = null;

    function requestPermission() {
        if (Notification.permission === 'default') {
            Notification.requestPermission();
        }
    }

    function playBeep() {
        try {
            if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();
            const oscillator = audioCtx.createOscillator();
            const gain = audioCtx.createGain();
            oscillator.connect(gain);
            gain.connect(audioCtx.destination);
            oscillator.frequency.value = 800;
            oscillator.type = 'sine';
            gain.gain.setValueAtTime(0.5, audioCtx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.8);
            oscillator.start(audioCtx.currentTime);
            oscillator.stop(audioCtx.currentTime + 0.8);
        } catch (e) {}
    }

    function showReminder(med) {
        if (notifiedIds.has(med.id)) return;
        notifiedIds.add(med.id);

        playBeep();

        if (Notification.permission === 'granted') {
            try {
                const notif = new Notification('Medicine Reminder', {
                    body: 'Time to take ' + med.name + ' (' + med.dosage + ')',
                    tag: 'med-reminder-' + med.id,
                    requireInteraction: true
                });
                setTimeout(function() { notif.close(); }, 15000);
            } catch (e) {}
        }

        var container = document.getElementById('reminderAlerts');
        if (container) {
            var alert = document.createElement('div');
            alert.className = 'reminder-alert';
            alert.innerHTML = '<strong>Reminder:</strong> Time to take ' + escapeHtml(med.name) + ' (' + escapeHtml(med.dosage) + ')';
            container.appendChild(alert);
            setTimeout(function() { if (alert.parentNode) alert.parentNode.removeChild(alert); }, 15000);
        }
    }

    function escapeHtml(s) {
        if (!s) return '';
        return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
    }

    async function checkReminders() {
        try {
            if (window.location.pathname === '/index.html' || window.location.pathname === '/signup.html') return;
            const res = await fetch('/api/pending-reminders');
            const data = await res.json();
            if (data.success && data.reminders) {
                data.reminders.forEach(showReminder);
            }
        } catch (e) {}
    }

    requestPermission();
    checkReminders();
    setInterval(checkReminders, 30000);
})();
