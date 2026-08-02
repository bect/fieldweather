document.addEventListener('DOMContentLoaded', () => {
    fetchForecast();
    fetchRecords();
});

async function fetchForecast() {
    try {
        const response = await fetch('/api/forecast');
        if (!response.ok) throw new Error('Failed to fetch forecast');
        
        const data = await response.json();
        const container = document.getElementById('forecast-content');
        
        if (data.total_records_analyzed === 0) {
            container.innerHTML = `<p>No records available to generate a forecast.</p>`;
            return;
        }

        container.innerHTML = `
            <div class="forecast-stat">
                <div class="condition">${data.most_likely_condition}</div>
                <div class="details">
                    <span class="prob">${data.probability.toFixed(1)}% Probability</span>
                    <span style="color: var(--text-secondary); font-size: 0.9rem;">Based on ${data.total_records_analyzed} historical records</span>
                </div>
            </div>
            <p style="margin-top: 1rem; color: var(--text-secondary);">
                Statistical analysis indicates that <strong>${data.most_likely_condition}</strong> is the most frequently recorded condition in your dataset.
            </p>
        `;
    } catch (error) {
        console.error(error);
        document.getElementById('forecast-content').innerHTML = `<p style="color: #ef4444;">Error loading forecast data.</p>`;
    }
}

async function fetchRecords() {
    try {
        const response = await fetch('/api/records');
        if (!response.ok) throw new Error('Failed to fetch records');
        
        const records = await response.json();
        const tbody = document.getElementById('records-body');
        
        if (!records || records.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-secondary);">No records synced yet.</td></tr>`;
            return;
        }

        tbody.innerHTML = '';
        records.forEach(record => {
            const tr = document.createElement('tr');
            
            // Format timestamp nicely
            const date = new Date(record.timestamp);
            const dateStr = date.toLocaleString(undefined, { 
                year: 'numeric', month: 'short', day: 'numeric',
                hour: '2-digit', minute: '2-digit'
            });

            tr.innerHTML = `
                <td>#${record.local_id}</td>
                <td>${dateStr}</td>
                <td>${record.location_name || 'Unknown'}</td>
                <td><span class="condition-badge">${record.condition}</span></td>
                <td>${record.latitude.toFixed(4)}</td>
                <td>${record.longitude.toFixed(4)}</td>
            `;
            tbody.appendChild(tr);
        });
    } catch (error) {
        console.error(error);
        const tbody = document.getElementById('records-body');
        tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: #ef4444;">Error loading records.</td></tr>`;
    }
}
