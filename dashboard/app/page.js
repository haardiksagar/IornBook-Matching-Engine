'use client';
import { useOrderBook } from './hooks/useOrderBook';
import { useTrades } from './hooks/useTrades';
import StatsBar from './components/StatsBar';
import OrderBook from './components/OrderBook';
import TradesFeed from './components/TradesFeed';
import DepthChart from './components/DepthChart';

export default function Dashboard() {
  const { bids, asks } = useOrderBook(15);
  const { trades, isConnected } = useTrades(50);

  return (
    <main style={{ maxWidth: '1400px', margin: '0 auto', paddingBottom: '40px' }}>
      
      <div style={{ textAlign: 'center', marginBottom: '40px' }}>
        <h1 style={{ 
          fontSize: '2.5rem', 
          fontWeight: '700', 
          margin: '0', 
          background: 'linear-gradient(to right, #60a5fa, #a78bfa)',
          WebkitBackgroundClip: 'text',
          WebkitTextFillColor: 'transparent',
          letterSpacing: '-0.02em'
        }}>
          IronBook Dashboard
        </h1>
        <p style={{ color: 'var(--text-muted)', marginTop: '10px', fontSize: '1.1rem' }}>
          Ultra-low latency limit order book • LMAX Disruptor Architecture
        </p>
      </div>

      <StatsBar bids={bids} asks={asks} trades={trades} />

      <div style={{ display: 'flex', gap: '24px' }}>
        <OrderBook bids={bids} asks={asks} />
        <TradesFeed trades={trades} isConnected={isConnected} />
      </div>

      <DepthChart bids={bids} asks={asks} />

    </main>
  );
}
