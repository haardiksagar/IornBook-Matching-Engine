import { useState, useEffect } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

export function useTrades(maxTrades = 50) {
  const [trades, setTrades] = useState([]);
  const [isConnected, setIsConnected] = useState(false);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws-engine'),
      reconnectDelay: 5000,
      debug: (str) => {
        // console.log(str); // Uncomment for debugging
      },
      onConnect: () => {
        setIsConnected(true);
        client.subscribe('/topic/trades', (message) => {
          const trade = JSON.parse(message.body);
          
          setTrades((prevTrades) => {
            // Prepend new trade, keep up to maxTrades
            const newTrades = [trade, ...prevTrades];
            return newTrades.slice(0, maxTrades);
          });
        });
      },
      onDisconnect: () => {
        setIsConnected(false);
      },
      onStompError: (frame) => {
        console.error('Broker reported error: ' + frame.headers['message']);
        console.error('Additional details: ' + frame.body);
      },
    });

    client.activate();

    return () => {
      client.deactivate();
    };
  }, [maxTrades]);

  return { trades, isConnected };
}
