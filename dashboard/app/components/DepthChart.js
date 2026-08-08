'use client';
import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';

export default function DepthChart({ bids, asks }) {
  // To draw a depth chart, we need cumulative volume.
  // Bids: Highest price to lowest price
  let cumulativeBid = 0;
  const bidData = [...bids].map(b => {
    cumulativeBid += b.volume;
    return { price: b.price, bidVolume: cumulativeBid, askVolume: null };
  }).reverse(); // Reverse so x-axis goes from low price to high price

  // Asks: Lowest price to highest price
  let cumulativeAsk = 0;
  const askData = [...asks].map(a => {
    cumulativeAsk += a.volume;
    return { price: a.price, bidVolume: null, askVolume: cumulativeAsk };
  });

  const data = [...bidData, ...askData];

  return (
    <div className="glass-panel" style={{ padding: '24px', marginTop: '24px', height: '300px', display: 'flex', flexDirection: 'column' }}>
      <h2 style={{ fontSize: '1.25rem', marginBottom: '20px' }}>Market Depth</h2>
      
      <div style={{ flex: 1, width: '100%' }}>
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data} margin={{ top: 0, right: 0, left: 0, bottom: 0 }}>
            <defs>
              <linearGradient id="colorBid" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="var(--buy-color)" stopOpacity={0.5}/>
                <stop offset="95%" stopColor="var(--buy-color)" stopOpacity={0}/>
              </linearGradient>
              <linearGradient id="colorAsk" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="var(--sell-color)" stopOpacity={0.5}/>
                <stop offset="95%" stopColor="var(--sell-color)" stopOpacity={0}/>
              </linearGradient>
            </defs>
            <XAxis dataKey="price" stroke="var(--text-muted)" fontSize={12} tickFormatter={(val) => val.toFixed(0)} />
            <YAxis stroke="var(--text-muted)" fontSize={12} orientation="right" />
            <Tooltip 
              contentStyle={{ backgroundColor: 'var(--bg-main)', border: '1px solid var(--border-card)' }}
              labelStyle={{ color: 'var(--text-main)' }}
            />
            <Area type="step" dataKey="bidVolume" stroke="var(--buy-color)" fillOpacity={1} fill="url(#colorBid)" isAnimationActive={false} />
            <Area type="step" dataKey="askVolume" stroke="var(--sell-color)" fillOpacity={1} fill="url(#colorAsk)" isAnimationActive={false} />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
