import { useState, useEffect } from 'react';

export function useOrderBook(levels = 15) {
  const [bids, setBids] = useState([]);
  const [asks, setAsks] = useState([]);

  useEffect(() => {
    let mounted = true;

    const fetchOrderBook = async () => {
      try {
        const response = await fetch(`/api/book?levels=${levels}`);
        if (!response.ok) throw new Error('Failed to fetch');
        
        const data = await response.json();
        
        if (mounted) {
          setBids(data.topBids || []);
          setAsks(data.topAsks || []);
        }
      } catch (err) {
        console.error("Error fetching order book:", err);
      }
    };

    fetchOrderBook(); // Initial fetch
    
    // Poll every 1 second
    const intervalId = setInterval(fetchOrderBook, 1000);

    return () => {
      mounted = false;
      clearInterval(intervalId);
    };
  }, [levels]);

  return { bids, asks };
}
