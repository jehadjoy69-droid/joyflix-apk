/**
 * JOYFLIX OFFICIAL WEBSITE - ULTRA-SMART JAVASCRIPT ENGINE
 * Handles dynamic GitHub releases, particle canvas, watch party simulator,
 * device switcher, QR modal, copy to clipboard, and FAQ accordions.
 */

document.addEventListener('DOMContentLoaded', () => {
    // 1. Current Year
    const yearSpan = document.getElementById('currentYear');
    if (yearSpan) yearSpan.textContent = new Date().getFullYear();

    // 2. Background Stardust Canvas
    initCinemaParticles();

    // 3. Cloudflare Latest Release Fetching (Android & Windows)
    fetchLatestCloudflareRelease();

    // 4. Interactive Live Watch Party Simulator & Reaction Engine
    initPlayerSimulator();

    // 5. Device Experience Switcher (Phone vs TV)
    initDeviceSwitcher();

    // 6. QR Code Modal
    initQrModal();

    // 7. Streaming Catalog & Live Search
    initStreamingCatalog();
    initLiveSearch();
    initCinemaModal();

    // 8. FAQ Accordion
    initFaqAccordion();

    // 9. Mobile Navigation Toggle & Bottom Dock
    initMobileNav();
    initMobileBottomNav();
});

/**
 * Background Stardust Cinema Canvas Particle Animation
 */
function initCinemaParticles() {
    const canvas = document.getElementById('cinemaCanvas');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    let width = canvas.width = window.innerWidth;
    let height = canvas.height = window.innerHeight;

    window.addEventListener('resize', () => {
        width = canvas.width = window.innerWidth;
        height = canvas.height = window.innerHeight;
    });

    const particles = [];
    const count = Math.min(width > 768 ? 65 : 30, 80);

    for (let i = 0; i < count; i++) {
        particles.push({
            x: Math.random() * width,
            y: Math.random() * height,
            radius: Math.random() * 1.5 + 0.5,
            vx: (Math.random() - 0.5) * 0.3,
            vy: (Math.random() - 0.5) * 0.3,
            alpha: Math.random() * 0.5 + 0.1,
            color: Math.random() > 0.3 ? '229, 9, 20' : '88, 101, 242'
        });
    }

    function render() {
        ctx.clearRect(0, 0, width, height);

        particles.forEach(p => {
            p.x += p.vx;
            p.y += p.vy;

            if (p.x < 0) p.x = width;
            if (p.x > width) p.x = 0;
            if (p.y < 0) p.y = height;
            if (p.y > height) p.y = 0;

            ctx.beginPath();
            ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2);
            ctx.fillStyle = `rgba(${p.color}, ${p.alpha})`;
            ctx.fill();
        });

        requestAnimationFrame(render);
    }
    render();
}

/**
 * Fetches Latest Release from Cloudflare Worker API (Android & Windows)
 */
async function fetchLatestCloudflareRelease() {
    const apiUrl = 'https://version.jehadjoy69.workers.dev/';

    const primaryBtn = document.getElementById('primaryDownloadBtn');
    const secondaryBtn = document.getElementById('secondaryDownloadBtn');
    const windowsBtn = document.getElementById('windowsDownloadBtn');
    const secondaryWinBtn = document.getElementById('secondaryWinDownloadBtn');
    const watchApkBtn = document.getElementById('watchApkBtn');
    const watchWinBtn = document.getElementById('watchWinBtn');
    const apkVerText = document.getElementById('apkVerText');
    const pillVersion = document.getElementById('pillVersion');
    const btnVersionBadge = document.getElementById('btnVersionBadge');
    const btnSizeBadge = document.getElementById('btnSizeBadge');
    const btnWinVersionBadge = document.getElementById('btnWinVersionBadge');
    const btnWinSizeBadge = document.getElementById('btnWinSizeBadge');
    const qrImage = document.querySelector('.qr-image');
    const qrDirectLink = document.querySelector('.modal-footer-link a');
    const dynVersions = document.querySelectorAll('.dyn-version');

    try {
        const response = await fetch(apiUrl);
        if (!response.ok) throw new Error('Worker response was not ok');
        const data = await response.json();

        // 1. Android Release
        if (data.android) {
            const androidVer = data.android.version || '5.0.18';
            const androidTag = androidVer.startsWith('v') ? androidVer : `v${androidVer}`;
            const cleanAndroidVer = androidVer.replace(/^v/i, '');
            const androidUrl = data.android.downloadUrl || 'https://github.com/jehadjoy69-droid/joyflix-apk/releases/download/v5.0.18/JoyFlix-v5.0.18.apk';
            const androidSize = data.android.size ? `${(data.android.size / (1024 * 1024)).toFixed(1)} MB` : '77.4 MB';

            if (primaryBtn) primaryBtn.href = androidUrl;
            if (secondaryBtn) secondaryBtn.href = androidUrl;
            if (watchApkBtn) watchApkBtn.href = androidUrl;
            if (apkVerText) apkVerText.textContent = androidTag;
            if (pillVersion) pillVersion.textContent = cleanAndroidVer;
            if (btnVersionBadge) btnVersionBadge.textContent = androidTag;
            if (btnSizeBadge) btnSizeBadge.textContent = androidSize;

            if (qrImage) {
                qrImage.src = `https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=${encodeURIComponent(androidUrl)}&color=E50914&bgcolor=0E1017`;
            }
            if (qrDirectLink) {
                qrDirectLink.href = androidUrl;
                qrDirectLink.textContent = `Download JoyFlix ${androidTag} APK`;
            }

            dynVersions.forEach(span => {
                span.textContent = span.textContent.startsWith('v') ? androidTag : cleanAndroidVer;
            });
        }

        // 2. Windows PC Release
        if (data.windows) {
            const winVer = data.windows.version || '5.0.10';
            const winTag = winVer.startsWith('v') ? winVer : `v${winVer}`;
            const winUrl = data.windows.downloadUrl || 'https://github.com/jehadjoy69-droid/joyflix-apk/releases/download/v5.0.12/JoyFlix-Windows-x64-5.0.10.msi';
            const winSize = data.windows.size ? `${(data.windows.size / (1024 * 1024)).toFixed(0)} MB` : '190 MB';

            if (windowsBtn) windowsBtn.href = winUrl;
            if (secondaryWinBtn) secondaryWinBtn.href = winUrl;
            if (watchWinBtn) watchWinBtn.href = winUrl;
            if (btnWinVersionBadge) btnWinVersionBadge.textContent = winTag;
            if (btnWinSizeBadge) btnWinSizeBadge.textContent = winSize;
        }
    } catch (e) {
        console.warn('Using default Cloudflare release fallback:', e);
    }
}

/**
 * Interactive Live Watch Party Simulator
 */
function initPlayerSimulator() {
    const playBtn = document.getElementById('simulatorPlayBtn');
    const progressBar = document.getElementById('simulatedProgress');
    const timeDisplay = document.getElementById('simTimeCurrent');
    const chatFeed = document.getElementById('playerChatFeed');
    const reactionContainer = document.getElementById('reactionContainer');
    const reactionBtns = document.querySelectorAll('.reaction-btn');

    let isPlaying = true;
    let progressPercent = 52.4;
    let totalSeconds = (1 * 3600) + (42 * 60) + 15;

    // 1. Ticking Progress Simulator
    setInterval(() => {
        if (!isPlaying) return;
        progressPercent += 0.04;
        totalSeconds += 1;
        if (progressPercent > 100) progressPercent = 0;

        if (progressBar) progressBar.style.width = `${progressPercent}%`;

        if (timeDisplay) {
            const hrs = String(Math.floor(totalSeconds / 3600)).padStart(2, '0');
            const mins = String(Math.floor((totalSeconds % 3600) / 60)).padStart(2, '0');
            const secs = String(totalSeconds % 60).padStart(2, '0');
            timeDisplay.textContent = `${hrs}:${mins}:${secs}`;
        }
    }, 1000);

    // Play/Pause button
    if (playBtn) {
        playBtn.addEventListener('click', () => {
            isPlaying = !isPlaying;
            playBtn.innerHTML = isPlaying 
                ? '<svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16"></rect><rect x="14" y="4" width="4" height="16"></rect></svg>'
                : '<svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>';
            
            spawnChatToast('Host (Jehad)', isPlaying ? 'Resumed playback for everyone ▶' : 'Paused room ⏸');
        });
    }

    // 2. Simulated Chat Toasts
    const cannedMessages = [
        { sender: 'Sarah', text: 'Bro that wormhole CGI is breathtaking! 🚀' },
        { sender: 'Alex', text: 'Audio in Dolby 5.1 is insane 🔥' },
        { sender: 'Jehad (Host)', text: 'Synced 0ms lag on all devices ⚡' },
        { sender: 'Tanjim', text: 'Wait pause for snacks 🍿' },
        { sender: 'Sarah', text: 'Hans Zimmer music never fails 😭❤️' }
    ];

    let messageIndex = 0;
    setInterval(() => {
        if (!isPlaying || !chatFeed) return;
        const msg = cannedMessages[messageIndex % cannedMessages.length];
        messageIndex++;
        spawnChatToast(msg.sender, msg.text);
    }, 4500);

    function spawnChatToast(sender, text) {
        if (!chatFeed) return;
        const toast = document.createElement('div');
        toast.className = 'chat-toast';
        toast.innerHTML = `<span class="chat-sender">${sender}:</span> <span>${text}</span>`;
        chatFeed.appendChild(toast);

        // Keep maximum 2 toasts at a time
        while (chatFeed.children.length > 2) {
            chatFeed.removeChild(chatFeed.firstChild);
        }

        setTimeout(() => {
            if (toast.parentNode === chatFeed) {
                chatFeed.removeChild(toast);
            }
        }, 4200);
    }

    // 3. Clickable Reaction Emojis Burst
    reactionBtns.forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            const emoji = btn.getAttribute('data-emoji') || '🔥';
            spawnFloatingEmoji(emoji);
        });
    });

    function spawnFloatingEmoji(emoji) {
        if (!reactionContainer) return;
        const span = document.createElement('span');
        span.className = 'floating-emoji';
        span.textContent = emoji;
        span.style.left = `${Math.random() * 30}px`;
        reactionContainer.appendChild(span);

        setTimeout(() => {
            if (span.parentNode === reactionContainer) {
                reactionContainer.removeChild(span);
            }
        }, 2200);
    }
}

/**
 * Dual Device Switcher (Mobile vs TV)
 */
function initDeviceSwitcher() {
    const tabs = document.querySelectorAll('.device-tab');
    const viewMobile = document.getElementById('viewMobile');
    const viewTv = document.getElementById('viewTv');

    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            tabs.forEach(t => t.classList.remove('active'));
            tab.classList.add('active');

            const device = tab.getAttribute('data-device');
            if (device === 'mobile') {
                viewMobile.classList.add('active');
                viewTv.classList.remove('active');
            } else {
                viewTv.classList.add('active');
                viewMobile.classList.remove('active');
            }
        });
    });
}


/**
 * Copy to Clipboard with Toast Notification
 */
window.copyText = function(inputId, btn) {
    const input = document.getElementById(inputId);
    if (!input) return;

    input.select();
    input.setSelectionRange(0, 99999);
    navigator.clipboard.writeText(input.value).then(() => {
        const originalText = btn.textContent;
        btn.textContent = 'Copied!';
        btn.style.background = '#22C55E';
        btn.style.color = '#000';

        showToast('Copied to clipboard!');

        setTimeout(() => {
            btn.textContent = originalText;
            btn.style.background = '';
            btn.style.color = '';
        }, 2000);
    }).catch(err => {
        console.error('Failed to copy text: ', err);
    });
};

function showToast(msg) {
    const toast = document.getElementById('toastNotify');
    if (!toast) return;
    toast.textContent = msg;
    toast.classList.add('show');
    setTimeout(() => {
        toast.classList.remove('show');
    }, 2500);
}

/**
 * QR Code Modal
 */
function initQrModal() {
    const qrBtn = document.getElementById('qrBtn');
    const qrModal = document.getElementById('qrModal');
    const qrClose = document.getElementById('qrClose');

    if (!qrBtn || !qrModal) return;

    qrBtn.addEventListener('click', () => {
        qrModal.classList.add('active');
    });

    if (qrClose) {
        qrClose.addEventListener('click', () => {
            qrModal.classList.remove('active');
        });
    }

    qrModal.addEventListener('click', (e) => {
        if (e.target === qrModal) {
            qrModal.classList.remove('active');
        }
    });
}

/**
 * FAQ Accordion
 */
function initFaqAccordion() {
    const faqItems = document.querySelectorAll('.faq-item');

    faqItems.forEach(item => {
        const questionBtn = item.querySelector('.faq-question');
        const answer = item.querySelector('.faq-answer');

        if (questionBtn && answer) {
            questionBtn.addEventListener('click', () => {
                const isActive = item.classList.contains('active');

                // Close other open items
                faqItems.forEach(otherItem => {
                    otherItem.classList.remove('active');
                    const otherAnswer = otherItem.querySelector('.faq-answer');
                    if (otherAnswer) otherAnswer.style.maxHeight = null;
                });

                if (!isActive) {
                    item.classList.add('active');
                    answer.style.maxHeight = answer.scrollHeight + 'px';
                } else {
                    item.classList.remove('active');
                    answer.style.maxHeight = null;
                }
            });
        }
    });
}

/**
 * Mobile Navigation Menu Toggle
 */
function initMobileNav() {
    const menuToggle = document.getElementById('menuToggle');
    const navLinks = document.getElementById('navLinks');

    if (menuToggle && navLinks) {
        menuToggle.addEventListener('click', (e) => {
            e.stopPropagation();
            navLinks.classList.toggle('mobile-open');
        });

        // Close menu on click of any nav link
        navLinks.querySelectorAll('a').forEach(link => {
            link.addEventListener('click', () => {
                navLinks.classList.remove('mobile-open');
            });
        });

        // Close menu when clicking outside
        document.addEventListener('click', (e) => {
            if (!navLinks.contains(e.target) && !menuToggle.contains(e.target)) {
                navLinks.classList.remove('mobile-open');
            }
        });
    }
}

/**
 * JoyFlix OAuth Bridge
 * Automatically intercepts MAL, AniList, and Simkl OAuth redirects and passes
 * tokens/codes back to the JoyFlix Android App via joyflixapp:// custom deep link.
 */
function initOAuthBridge() {
    const search = window.location.search || '';
    const hash = window.location.hash || '';
    const pathname = window.location.pathname.toLowerCase();

    const hasCode = search.includes('code=');
    const hasToken = hash.includes('access_token=') || search.includes('access_token=');
    const hasError = search.includes('error=') || hash.includes('error=');

    if (!hasCode && !hasToken && !hasError && !pathname.includes('anilist') && !pathname.includes('mal') && !pathname.includes('simkl')) {
        return;
    }

    let serviceName = 'JoyFlix';
    let deepLink = '';

    // 1. AniList (Implicit token or code)
    if (hasToken || pathname.includes('anilist')) {
        serviceName = 'AniList';
        const params = hash ? ('?' + hash.replace(/^#\/?/, '')) : search;
        deepLink = `joyflixapp://anilistlogin${params}`;
    }
    // 2. MAL (MyAnimeList - state contains RequestID or mal in path)
    else if (search.includes('RequestID') || pathname.includes('mal')) {
        serviceName = 'MyAnimeList (MAL)';
        deepLink = `joyflixapp://mallogin${search}`;
    }
    // 3. Simkl (code & state or simkl in path)
    else if (pathname.includes('simkl') || hasCode) {
        serviceName = 'Simkl';
        deepLink = `joyflixapp://simkllogin${search}`;
    }

    if (!deepLink) return;

    // Trigger instant deep link redirect
    try {
        window.location.href = deepLink;
    } catch (e) {
        console.warn('Auto redirect error:', e);
    }

    function renderModal() {
        if (document.getElementById('oauthBridgeModal')) return;

        const overlay = document.createElement('div');
        overlay.id = 'oauthBridgeModal';
        overlay.setAttribute('style', 'position: fixed; inset: 0; z-index: 999999; background: rgba(5, 7, 15, 0.95); backdrop-filter: blur(16px); -webkit-backdrop-filter: blur(16px); display: flex; align-items: center; justify-content: center; padding: 20px; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;');
        
        overlay.innerHTML = `
            <div style="background: #111422; border: 1px solid rgba(229, 9, 20, 0.4); box-shadow: 0 20px 50px rgba(0,0,0,0.85), 0 0 35px rgba(229, 9, 20, 0.25); border-radius: 20px; max-width: 440px; width: 100%; padding: 32px; text-align: center; color: #fff;">
                <div style="width: 68px; height: 68px; margin: 0 auto 20px; background: rgba(229,9,20,0.15); border-radius: 50%; display: flex; align-items: center; justify-content: center; border: 1px solid rgba(229,9,20,0.4);">
                    <svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="#e50914" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"></path>
                        <polyline points="10 17 15 12 10 7"></polyline>
                        <line x1="15" y1="12" x2="3" y2="12"></line>
                    </svg>
                </div>
                <h3 style="font-size: 22px; font-weight: 700; margin-bottom: 8px; color: #fff;">Connecting to JoyFlix</h3>
                <p style="font-size: 14px; color: #9ca3af; line-height: 1.6; margin-bottom: 24px;">
                    Authorizing with <strong style="color: #fff;">${serviceName}</strong>. Opening the JoyFlix app to complete your login...
                </p>
                <a id="oauthLaunchBtn" href="${deepLink}" style="display: block; width: 100%; padding: 14px 20px; background: linear-gradient(135deg, #e50914 0%, #b80610 100%); color: #fff; font-size: 15px; font-weight: 600; text-decoration: none; border-radius: 12px; box-shadow: 0 4px 15px rgba(229,9,20,0.4); margin-bottom: 12px; box-sizing: border-box;">
                    🚀 Open JoyFlix App
                </a>
                <button id="oauthCancelBtn" style="background: transparent; border: none; color: #6b7280; font-size: 13px; cursor: pointer; padding: 6px 12px;">
                    Stay on Website
                </button>
            </div>
        `;

        document.body.appendChild(overlay);

        const cancelBtn = document.getElementById('oauthCancelBtn');
        if (cancelBtn) {
            cancelBtn.addEventListener('click', () => {
                overlay.remove();
            });
        }
    }

    if (document.body) {
        renderModal();
    } else {
        document.addEventListener('DOMContentLoaded', renderModal);
    }
}

// Execute OAuth bridge immediately on script load
initOAuthBridge();


/* ==========================================================
   JOYFLIX STREAMING ENGINE & VIDRIFT EMBED CONTROLLER
   ========================================================== */

const TMDB_API_KEY = "70e72ab91e1180976a71072fbcf313db";
const TMDB_BASE_URL = "https://api.themoviedb.org/3";

let currentModalMedia = {
    id: null,
    type: 'movie',
    title: '',
    season: 1,
    episode: 1,
    isDub: false
};

const catalogCache = {};

/**
 * 1. Initialize Streaming Catalog & Tabs
 */
async function initStreamingCatalog() {
    const catalogGrid = document.getElementById('catalogGrid');
    if (!catalogGrid) return;

    const tabs = document.querySelectorAll('.catalog-tab');
    
    // Check if target tab was saved in localStorage from navigation
    const targetTab = localStorage.getItem('targetTab') || 'trending';
    localStorage.removeItem('targetTab');

    tabs.forEach(tab => {
        if (tab.dataset.tab === targetTab) {
            tabs.forEach(t => t.classList.remove('active'));
            tab.classList.add('active');
        }

        tab.addEventListener('click', () => {
            tabs.forEach(t => t.classList.remove('active'));
            tab.classList.add('active');
            loadCatalogCategory(tab.dataset.tab);
        });
    });

    loadCatalogCategory(targetTab);
}

async function loadCatalogCategory(category) {
    const grid = document.getElementById('catalogGrid');
    if (!grid) return;

    if (catalogCache[category]) {
        renderCatalogItems(catalogCache[category]);
        return;
    }

    grid.innerHTML = '<div style="grid-column: 1/-1; text-align: center; padding: 3rem; color: var(--text-muted); font-size: 0.95rem;">Loading streaming titles...</div>';

    let endpoint = '/trending/all/day';
    if (category === 'movies') {
        endpoint = '/movie/popular';
    } else if (category === 'tv') {
        endpoint = '/tv/popular';
    } else if (category === 'anime') {
        endpoint = '/discover/tv?with_genres=16&with_original_language=ja&sort_by=popularity.desc';
    } else if (category === 'top_rated') {
        endpoint = '/movie/top_rated';
    }

    try {
        const sep = endpoint.includes('?') ? '&' : '?';
        const url = `${TMDB_BASE_URL}${endpoint}${sep}api_key=${TMDB_API_KEY}&language=en-US`;
        const res = await fetch(url);
        if (!res.ok) throw new Error('Catalog fetch error');
        const data = await res.json();
        const results = (data.results || []).filter(item => item.poster_path);
        
        catalogCache[category] = results;
        renderCatalogItems(results);
    } catch (e) {
        console.warn('Catalog loading error:', e);
        grid.innerHTML = '<div style="grid-column: 1/-1; text-align: center; padding: 3rem; color: var(--text-muted);">Failed to load titles. Please try again.</div>';
    }
}

function renderCatalogItems(items) {
    const grid = document.getElementById('catalogGrid');
    if (!grid) return;

    grid.innerHTML = '';
    if (!items || !items.length) {
        grid.innerHTML = '<div style="grid-column: 1/-1; text-align: center; padding: 3rem; color: var(--text-muted);">No titles found.</div>';
        return;
    }

    items.slice(0, 18).forEach(item => {
        const type = item.media_type || (item.first_air_date ? 'tv' : 'movie');
        const title = item.title || item.name || 'Untitled';
        const year = (item.release_date || item.first_air_date || '').split('-')[0] || '2026';
        const rating = (item.vote_average || 7.5).toFixed(1);
        const posterUrl = `https://image.tmdb.org/t/p/w500${item.poster_path}`;

        const watchUrl = `watch.html?id=${item.id}&type=${type}&s=1&ep=1&title=${encodeURIComponent(title)}`;
        const card = document.createElement('a');
        card.href = watchUrl;
        card.className = 'media-card';
        card.innerHTML = `
            <div class="media-poster-box">
                <img src="${posterUrl}" alt="${title}" class="media-poster" loading="lazy" onerror="this.src='assets/logo.png'">
                <div class="media-badge-top-left">
                    <span class="badge-pill-small badge-quality">4K</span>
                </div>
                <div class="media-badge-top-right">
                    <span class="badge-pill-small badge-rating">★ ${rating}</span>
                </div>
                <div class="media-play-overlay">
                    <div class="media-play-circle">
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"/></svg>
                    </div>
                </div>
            </div>
            <div class="media-card-content">
                <h4 class="media-card-title">${title}</h4>
                <div class="media-card-meta">
                    <span style="text-transform: uppercase; font-weight: 700; color: var(--text-secondary);">${type}</span>
                    <span>${year}</span>
                </div>
            </div>
        `;

        grid.appendChild(card);
    });
}

/**
 * 2. Instant Live Search Engine
 */
function initLiveSearch() {
    const input = document.getElementById('streamSearchInput');
    const dropdown = document.getElementById('liveSearchResults');
    const clearBtn = document.getElementById('streamSearchClear');
    if (!input || !dropdown) return;

    let searchTimer = null;

    input.addEventListener('input', () => {
        const q = input.value.trim();
        if (clearBtn) clearBtn.style.display = q ? 'flex' : 'none';

        clearTimeout(searchTimer);
        if (q.length < 2) {
            dropdown.classList.remove('active');
            dropdown.innerHTML = '';
            return;
        }

        searchTimer = setTimeout(() => executeSearch(q), 300);
    });

    if (clearBtn) {
        clearBtn.addEventListener('click', () => {
            input.value = '';
            clearBtn.style.display = 'none';
            dropdown.classList.remove('active');
            dropdown.innerHTML = '';
        });
    }

    // Close on outside click
    document.addEventListener('click', (e) => {
        if (!e.target.closest('.stream-search-wrapper')) {
            dropdown.classList.remove('active');
        }
    });

    async function executeSearch(query) {
        dropdown.innerHTML = '<div style="padding: 1rem; color: var(--text-muted); font-size: 0.85rem;">Searching catalog...</div>';
        dropdown.classList.add('active');

        try {
            const url = `${TMDB_BASE_URL}/search/multi?api_key=${TMDB_API_KEY}&query=${encodeURIComponent(query)}&language=en-US`;
            const res = await fetch(url);
            if (!res.ok) throw new Error('Search failed');
            const data = await res.json();
            const results = (data.results || []).filter(item => item.media_type === 'movie' || item.media_type === 'tv');

            if (!results.length) {
                dropdown.innerHTML = '<div style="padding: 1rem; color: var(--text-muted); font-size: 0.85rem;">No results found.</div>';
                return;
            }

            dropdown.innerHTML = '';
            results.slice(0, 8).forEach(item => {
                const type = item.media_type;
                const title = item.title || item.name || 'Untitled';
                const year = (item.release_date || item.first_air_date || '').split('-')[0] || '';
                const thumb = item.poster_path ? `https://image.tmdb.org/t/p/w92${item.poster_path}` : 'assets/logo.png';
                const rating = item.vote_average ? `★ ${item.vote_average.toFixed(1)}` : '';

                const watchUrl = `watch.html?id=${item.id}&type=${type}&s=1&ep=1&title=${encodeURIComponent(title)}`;
                const row = document.createElement('a');
                row.href = watchUrl;
                row.className = 'search-result-item';
                row.style.textDecoration = 'none';
                row.style.color = 'inherit';
                row.innerHTML = `
                    <img src="${thumb}" alt="${title}" class="search-result-thumb" onerror="this.src='assets/logo.png'">
                    <div class="search-result-info">
                        <div class="search-result-title">${title}</div>
                        <div class="search-result-meta">
                            <span class="search-result-type">${type}</span>
                            ${year ? `<span>${year}</span>` : ''}
                            ${rating ? `<span style="color: #FFD700; font-weight: 700;">${rating}</span>` : ''}
                        </div>
                    </div>
                `;

                row.addEventListener('click', () => {
                    dropdown.classList.remove('active');
                });

                dropdown.appendChild(row);
            });
        } catch (e) {
            dropdown.innerHTML = '<div style="padding: 1rem; color: var(--text-muted); font-size: 0.85rem;">Search unavailable right now.</div>';
        }
    }
}

/**
 * 3. Cinema Player Modal & VidRift Controller
 */
function initCinemaModal() {
    const modal = document.getElementById('cinemaModal');
    const closeBtn = document.getElementById('cinemaCloseBtn');
    if (!modal) return;

    if (closeBtn) {
        closeBtn.addEventListener('click', closeCinemaModal);
    }

    modal.addEventListener('click', (e) => {
        if (e.target === modal) closeCinemaModal();
    });

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && modal.classList.contains('open')) {
            closeCinemaModal();
        }
    });

    // Auto-rotate phone to Landscape on Mobile when entering Fullscreen
    const handleModalFullscreen = async () => {
        const isMobile = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent) || window.innerWidth < 768;
        if (!isMobile) return;

        try {
            const isFullscreen = document.fullscreenElement || document.webkitFullscreenElement || document.mozFullScreenElement || document.msFullscreenElement;
            if (isFullscreen) {
                if (screen.orientation && screen.orientation.lock) {
                    await screen.orientation.lock('landscape').catch(() => {});
                }
            } else {
                if (screen.orientation && screen.orientation.unlock) {
                    screen.orientation.unlock();
                }
            }
        } catch (err) {
            console.warn('Modal orientation lock failed:', err);
        }
    };

    document.addEventListener('fullscreenchange', handleModalFullscreen);
    document.addEventListener('webkitfullscreenchange', handleModalFullscreen);
    document.addEventListener('mozfullscreenchange', handleModalFullscreen);
    document.addEventListener('MSFullscreenChange', handleModalFullscreen);

    // VidRift postMessage Communication
    window.addEventListener('message', (e) => {
        if (e.origin !== 'https://embed.vidrift.net') return;
        
        // Handle VidRift Fullscreen event
        if (e.data.type === 'vidrift:fullscreen') {
            if (e.data.on) {
                if (screen.orientation && screen.orientation.lock) {
                    screen.orientation.lock('landscape').catch(() => {});
                }
            } else {
                if (screen.orientation && screen.orientation.unlock) {
                    screen.orientation.unlock();
                }
            }
        }

        // Handle Exit button inside player
        if (e.data.type === 'vidrift:exit') {
            closeCinemaModal();
        }

        // Handle auto-next episode
        if (e.data.type === 'vidrift:nextup-play') {
            if (currentModalMedia.type === 'tv' || currentModalMedia.type === 'anime') {
                currentModalMedia.episode += 1;
                refreshModalPlayerUrl();
                updateModalEpisodeUI();
            }
        }
    });
}

function openCinemaPlayer(id, type, title, season = 1, episode = 1) {
    window.location.href = `watch.html?id=${id}&type=${(type || 'movie').toLowerCase()}&s=${parseInt(season, 10) || 1}&ep=${parseInt(episode, 10) || 1}&title=${encodeURIComponent(title || '')}`;
    return;

    currentModalMedia = {
        id,
        type: (type || 'movie').toLowerCase(),
        title: title || 'JoyFlix Stream',
        season: parseInt(season, 10) || 1,
        episode: parseInt(episode, 10) || 1,
        isDub: false
    };

    document.getElementById('modalTitleText').textContent = title;
    const fullPageBtn = document.getElementById('modalFullPageBtn');
    if (fullPageBtn) {
        fullPageBtn.href = `watch.html?id=${id}&type=${currentModalMedia.type}&s=${currentModalMedia.season}&ep=${currentModalMedia.episode}&title=${encodeURIComponent(title)}`;
    }

    refreshModalPlayerUrl();

    // TV / Anime Season Picker
    const isTv = currentModalMedia.type === 'tv' || currentModalMedia.type === 'anime';
    const sidebar = document.getElementById('modalEpisodesSidebar');
    if (sidebar) {
        if (isTv) {
            sidebar.style.display = 'flex';
            loadModalSeasons(id);
        } else {
            sidebar.style.display = 'none';
        }
    }

    modal.classList.add('open');
    document.body.classList.add('cinema-open');
    document.body.style.overflow = 'hidden';
}

function closeCinemaModal() {
    const modal = document.getElementById('cinemaModal');
    const iframe = document.getElementById('cinemaIframe');
    if (modal) modal.classList.remove('open');
    if (iframe) iframe.src = '';
    document.body.classList.remove('cinema-open');
    document.body.style.overflow = '';
}

function refreshModalPlayerUrl() {
    const iframe = document.getElementById('cinemaIframe');
    if (!iframe) return;

    const { id, type, title, season, episode, isDub } = currentModalMedia;
    const isTv = type === 'tv' || type === 'anime';
    const encodedTitle = encodeURIComponent(title || 'JoyFlix');
    const dubParam = isDub ? '&dub=1' : '';

    let url = '';
    if (isTv) {
        url = `https://embed.vidrift.net/embed/tv/${id}/${season}/${episode}?title=${encodedTitle}&brand=JOYFLIX&brandColor=E50914&brandLogo=https://joyflix.fun/assets/logo.png&watermark=1&showTitle=1&exit=1${dubParam}`;
    } else {
        url = `https://embed.vidrift.net/embed/movie/${id}?title=${encodedTitle}&brand=JOYFLIX&brandColor=E50914&brandLogo=https://joyflix.fun/assets/logo.png&watermark=1&showTitle=1&exit=1`;
    }

    iframe.src = url;
}

function toggleModalDub() {
    currentModalMedia.isDub = !currentModalMedia.isDub;
    const btn = document.getElementById('modalDubBtn');
    if (btn) {
        btn.classList.toggle('active', currentModalMedia.isDub);
        btn.textContent = currentModalMedia.isDub ? 'Audio: English Dub (ON)' : 'Audio: Default / Sub';
    }
    refreshModalPlayerUrl();
}

async function loadModalSeasons(tvId) {
    const sel = document.getElementById('modalSeasonSelect');
    if (!sel) return;

    try {
        const res = await fetch(`${TMDB_BASE_URL}/tv/${tvId}?api_key=${TMDB_API_KEY}&language=en-US`);
        const data = await res.json();
        const total = data.number_of_seasons || 1;

        sel.innerHTML = '';
        for (let i = 1; i <= total; i++) {
            const opt = document.createElement('option');
            opt.value = i;
            opt.textContent = `Season ${i}`;
            if (i === currentModalMedia.season) opt.selected = true;
            sel.appendChild(opt);
        }

        sel.onchange = () => {
            loadModalEpisodes(tvId, parseInt(sel.value, 10));
        };

        loadModalEpisodes(tvId, currentModalMedia.season);
    } catch (e) {
        console.warn('Modal season load error:', e);
    }
}

async function loadModalEpisodes(tvId, seasonNum) {
    const container = document.getElementById('modalEpisodesList');
    if (!container) return;

    container.innerHTML = '<p style="color: var(--text-muted); font-size: 0.8rem; padding: 10px;">Loading...</p>';

    try {
        const res = await fetch(`${TMDB_BASE_URL}/tv/${tvId}/season/${seasonNum}?api_key=${TMDB_API_KEY}&language=en-US`);
        const data = await res.json();
        const eps = data.episodes || [];

        container.innerHTML = '';
        eps.forEach(ep => {
            const item = document.createElement('div');
            const isActive = (seasonNum === currentModalMedia.season && ep.episode_number === currentModalMedia.episode);
            item.className = `episode-item-btn ${isActive ? 'active' : ''}`;
            const thumb = ep.still_path ? `https://image.tmdb.org/t/p/w300${ep.still_path}` : 'assets/logo.png';

            item.innerHTML = `
                <img src="${thumb}" alt="EP ${ep.episode_number}" class="episode-item-thumb" onerror="this.src='assets/logo.png'">
                <div class="episode-item-info">
                    <span class="episode-item-num">EP ${ep.episode_number}</span>
                    <div class="episode-item-name">${ep.name || `Episode ${ep.episode_number}`}</div>
                </div>
            `;

            item.onclick = () => {
                currentModalMedia.season = seasonNum;
                currentModalMedia.episode = ep.episode_number;

                document.querySelectorAll('#modalEpisodesList .episode-item-btn').forEach(b => b.classList.remove('active'));
                item.classList.add('active');

                refreshModalPlayerUrl();
            };

            container.appendChild(item);
        });
    } catch (e) {
        container.innerHTML = '<p style="color: var(--text-muted); font-size: 0.8rem;">Failed to load episodes.</p>';
    }
}

/**
 * Mobile Bottom Navigation Bar Interactivity
 */
function initMobileBottomNav() {
    const mobileNav = document.getElementById('mobileBottomNav');
    if (!mobileNav) return;

    const navItems = mobileNav.querySelectorAll('.mobile-nav-item');
    const searchTrigger = document.getElementById('mobileSearchTrigger');

    navItems.forEach(item => {
        item.addEventListener('click', () => {
            if (item.getAttribute('target') === '_blank') return;
            navItems.forEach(n => n.classList.remove('active'));
            item.classList.add('active');
        });
    });

    if (searchTrigger) {
        searchTrigger.addEventListener('click', () => {
            const catalogSection = document.getElementById('stream-catalog');
            const searchInput = document.getElementById('streamSearchInput');
            if (catalogSection) {
                catalogSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
            if (searchInput) {
                setTimeout(() => {
                    searchInput.focus();
                }, 450);
            }
        });
    }
}
