'use client';

export default function StatsBar({ bids, asks, trades }) {
  const bestBid = bids.length > 0 ? bids[0].price : 0;
  const bestAsk = asks.length > 0 ? asks[0].price : 0;
  const spread = bestAsk > 0 && bestBid > 0 ? (bestAsk - bestBid).toFixed(2) : '0.00';
  
  const totalTrades = trades.length; // Actually total trades seen in this session
  const totalVolume = trades.reduce((acc, t) => acc + t.quantity, 0);

  return (
    <div className="glass-panel" style={{ display: 'flex', justifyContent: 'space-between', padding: '16px 24px', marginBottom: '24px' }}>
      
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem', textTransform: 'uppercase' }}>24h Volume</span>
        <span className="mono" style={{ fontSize: '1.25rem', fontWeight: '600' }}>{totalVolume.toLocaleString()}</span>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column' }}>
        <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem', textTransform: 'uppercase' }}>Trades Executed</span>
        <span className="mono" style={{ fontSize: '1.25rem', fontWeight: '600' }}>{totalTrades.toLocaleString()}</span>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
        <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem', textTransform: 'uppercase' }}>Spread</span>
        <span className="mono" style={{ fontSize: '1.25rem', fontWeight: '600', color: 'var(--accent-color)' }}>{spread}</span>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end' }}>
        <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem', textTransform: 'uppercase' }}>Best Bid</span>
        <span className="mono" style={{ fontSize: '1.25rem', fontWeight: '600', color: 'var(--buy-color)' }}>{bestBid.toFixed(2)}</span>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end' }}>
        <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem', textTransform: 'uppercase' }}>Best Ask</span>
        <span className="mono" style={{ fontSize: '1.25rem', fontWeight: '600', color: 'var(--sell-color)' }}>{bestAsk.toFixed(2)}</span>
      </div>

    </div>
  );
}
