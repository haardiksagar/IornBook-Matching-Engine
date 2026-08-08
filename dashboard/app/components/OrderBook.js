'use client';

function maxVolume(levels) {
  return levels.reduce((max, level) => Math.max(max, level.volume), 0);
}

export default function OrderBook({ bids, asks }) {
  const maxBidVol = maxVolume(bids);
  const maxAskVol = maxVolume(asks);
  const maxTotalVol = Math.max(maxBidVol, maxAskVol) || 1; // avoid div by 0

  return (
    <div className="glass-panel" style={{ flex: 1, padding: '24px', display: 'flex', flexDirection: 'column' }}>
      
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h2 style={{ fontSize: '1.25rem', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <div className="live-dot" />
          Live Order Book
        </h2>
        <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>REST Snapshot</span>
      </div>

      <div style={{ display: 'flex', gap: '24px', flex: 1 }}>
        
        {/* BIDS COLUMN */}
        <div style={{ flex: 1 }}>
          <div style={{ padding: '8px', textAlign: 'center', background: 'var(--buy-bg)', color: 'var(--buy-color)', borderRadius: '6px', marginBottom: '12px', fontWeight: '600', fontSize: '0.9rem', letterSpacing: '0.05em' }}>
            BIDS (BUY)
          </div>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr>
                <th style={{ textAlign: 'left', color: 'var(--text-muted)', fontSize: '0.8rem', padding: '8px 0', borderBottom: '1px solid var(--border-card)' }}>Price</th>
                <th style={{ textAlign: 'right', color: 'var(--text-muted)', fontSize: '0.8rem', padding: '8px 0', borderBottom: '1px solid var(--border-card)' }}>Volume</th>
              </tr>
            </thead>
            <tbody>
              {bids.length === 0 ? (
                <tr><td colSpan="2" style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)', fontStyle: 'italic' }}>Waiting...</td></tr>
              ) : bids.map((bid, i) => {
                const percentage = (bid.volume / maxTotalVol) * 100;
                return (
                  <tr key={`bid-${bid.price}-${i}`} style={{ position: 'relative' }}>
                    <td style={{ position: 'absolute', right: 0, top: 0, bottom: 0, width: `${percentage}%`, background: 'var(--buy-bg)', zIndex: 0, transition: 'width 0.2s' }} />
                    <td className="mono" style={{ position: 'relative', zIndex: 1, padding: '8px 0', color: 'var(--buy-color)', fontWeight: '500' }}>{bid.price.toFixed(2)}</td>
                    <td className="mono" style={{ position: 'relative', zIndex: 1, padding: '8px 0', textAlign: 'right' }}>{bid.volume.toLocaleString()}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {/* ASKS COLUMN */}
        <div style={{ flex: 1 }}>
          <div style={{ padding: '8px', textAlign: 'center', background: 'var(--sell-bg)', color: 'var(--sell-color)', borderRadius: '6px', marginBottom: '12px', fontWeight: '600', fontSize: '0.9rem', letterSpacing: '0.05em' }}>
            ASKS (SELL)
          </div>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr>
                <th style={{ textAlign: 'left', color: 'var(--text-muted)', fontSize: '0.8rem', padding: '8px 0', borderBottom: '1px solid var(--border-card)' }}>Price</th>
                <th style={{ textAlign: 'right', color: 'var(--text-muted)', fontSize: '0.8rem', padding: '8px 0', borderBottom: '1px solid var(--border-card)' }}>Volume</th>
              </tr>
            </thead>
            <tbody>
              {asks.length === 0 ? (
                <tr><td colSpan="2" style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)', fontStyle: 'italic' }}>Waiting...</td></tr>
              ) : asks.map((ask, i) => {
                const percentage = (ask.volume / maxTotalVol) * 100;
                return (
                  <tr key={`ask-${ask.price}-${i}`} style={{ position: 'relative' }}>
                    <td style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: `${percentage}%`, background: 'var(--sell-bg)', zIndex: 0, transition: 'width 0.2s' }} />
                    <td className="mono" style={{ position: 'relative', zIndex: 1, padding: '8px 0', color: 'var(--sell-color)', fontWeight: '500' }}>{ask.price.toFixed(2)}</td>
                    <td className="mono" style={{ position: 'relative', zIndex: 1, padding: '8px 0', textAlign: 'right' }}>{ask.volume.toLocaleString()}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

      </div>
    </div>
  );
}
