const API_BASE = '';

async function fetchAPI(endpoint, options = {}) {
    try {
        const url = endpoint.startsWith('http') ? endpoint : `${API_BASE}${endpoint}`;
        const response = await fetch(url, {
            headers: {
                'Content-Type': 'application/json',
                ...options.headers
            },
            ...options
        });
        
        if (!response.ok) {
            const errBody = await response.text();
            throw new Error(errBody || response.statusText);
        }
        
        // Handle 204 No Content or empty responses
        const text = await response.text();
        return text ? JSON.parse(text) : {};
    } catch (error) {
        console.error('API Error:', error);
        throw error;
    }
}

// Global API helpers
const API = {
    getIncidents: () => fetchAPI('/incidents'),
    getWarehouses: (incidentId = '') => {
        const url = incidentId ? `/v1/warehouses?incidentId=${incidentId}` : '/v1/warehouses';
        return fetchAPI(url);
    },
    getInventory: (incidentId) => fetchAPI(`/inventory/${incidentId}`),
    donate: (payload) => fetchAPI('/donations', {
        method: 'POST',
        body: JSON.stringify(payload)
    }),
    requestAllocation: (payload) => fetchAPI('/allocations', {
        method: 'POST',
        body: JSON.stringify(payload)
    }),
    getHistory: (email, incidentId = '', page = 0) => fetchAPI(`/allocations/history?query=${encodeURIComponent(email)}&incidentId=${encodeURIComponent(incidentId)}&page=${page}&size=15`),
    cancelAllocation: (reqId) => fetchAPI(`/allocations/${reqId}/cancel`, {
        method: 'POST'
    }),
    dispatchPickupAllocation: (reqId) => fetchAPI(`/allocations/${reqId}/dispatch-pickup`, {
        method: 'POST'
    }),
    updateIncident: (id, data) => fetchAPI(`/incidents/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data)
    }),
    deleteIncident: (id) => fetchAPI(`/incidents/${id}`, {
        method: 'DELETE'
    }),
    createWarehouse: (payload) => fetchAPI('/v1/warehouses', {
        method: 'POST',
        body: JSON.stringify(payload)
    }),
    updateWarehouse: (id, payload) => fetchAPI(`/v1/warehouses/${id}`, {
        method: 'PUT',
        body: JSON.stringify(payload)
    })
};

// Common UI functions
function showToast(message, type = 'info') {
    const toast = document.createElement('div');
    toast.className = 'toast';
    
    if (type === 'success') toast.style.background = '#16A34A';
    else if (type === 'error') toast.style.background = '#DC2626';
    else if (type === 'warning') toast.style.background = '#D97706';
    else toast.style.background = '#F97316';
    
    toast.innerText = message;
    document.body.appendChild(toast);
    
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transition = 'opacity 0.4s ease';
        setTimeout(() => toast.remove(), 400);
    }, 3500);
}

// Load navigation template
document.addEventListener('DOMContentLoaded', () => {
    const navHTML = `
        <nav class="navbar">
            <div class="logo">TrackDonation</div>
            <div class="nav-links">
                <a href="index.html">Dashboard</a>
                <a href="donate.html">Donate</a>
                <a href="requestallocation.html">Request Supplies</a>
                <a href="history.html">History</a>
                <a href="management.html">Management</a>
            </div>
        </nav>
    `;
    
    if(document.body.firstChild) {
        document.body.insertAdjacentHTML('afterbegin', navHTML);
    }
    
    // Highlight active link
    const currentPath = window.location.pathname.split('/').pop() || 'index.html';
    document.querySelectorAll('.nav-links a').forEach(a => {
        if (a.getAttribute('href') === currentPath) {
            a.classList.add('active');
        }
    });
});
