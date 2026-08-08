'use client';
import { useEffect, useRef } from 'react';

export default function TradesFeed({ trades, isConnected }) {
  // To trigger animations only on new items, we can use a basic trick:
  // We'll apply the `animate-flash` class and rely on React re-rendering the row.

  return (
    <div className="glass-panel" style={{ flex: '0 0 350px', padding: '24px', display: 'flex', flexDirection: 'column', maxHeight: '600px' }}>
      
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h2 style={{ fontSize: '1.25rem', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <div className="live-dot" style={{ backgroundColor: isConnected ? 'var(--accent-color)' : 'var(--text-muted)', boxShadow: isConnected ? '0 0 8px var(--accent-color)' : 'none' }} />
          Recent Trades
        </h2>
        <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>WebSocket Stream</span>
      </div>

      <div style={{ overflowY: 'auto', flex: 1, paddingRight: '8px' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ position: 'sticky', top: 0, background: 'var(--bg-main)', zIndex: 10 }}>
              <th style={{ textAlign: 'left', color: 'var(--text-muted)', fontSize: '0.8rem', padding: '8px 0', borderBottom: '1px solid var(--border-card)' }}>Time</th>
              <th style={{ textAlign: 'right', color: 'var(--text-muted)', fontSize: '0.8rem', padding: '8px 0', borderBottom: '1px solid var(--border-card)' }}>Price</th>
              <th style={{ textAlign: 'right', color: 'var(--text-muted)', fontSize: '0.8rem', padding: '8px 0', borderBottom: '1px solid var(--border-card)' }}>Qty</th>
            </tr>
          </thead>
          <tbody>
            {trades.length === 0 ? (
              <tr><td colSpan="3" style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted)', fontStyle: 'italic' }}>No trades yet...</td></tr>
            ) : trades.map((trade, i) => {
              const date = new Date(trade.timestamp);
              const timeStr = date.toLocaleTimeString('en-US', { hour12: false }) + '.' + date.getMilliseconds().toString().padStart(3, '0');
              
              // Only flash the most recent trade that arrives at index 0
              const rowClass = i === 0 ? "animate-flash" : "";

              return (
                <tr key={`${trade.tradeId}-${i}`} className={rowClass} style={{ borderBottom: '1px solid rgba(255,255,255,0.02)' }}>
                  <td className="mono" style={{ color: 'var(--text-muted)', fontSize: '0.8rem', padding: '8px 0' }}>{timeStr}</td>
                  <td className="mono" style={{ textAlign: 'right', fontWeight: '500', padding: '8px 0' }}>{trade.price.toFixed(2)}</td>
                  <td className="mono" style={{ textAlign: 'right', padding: '8px 0' }}>{trade.quantity.toLocaleString()}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
