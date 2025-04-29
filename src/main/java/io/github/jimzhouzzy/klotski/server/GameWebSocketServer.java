package io.github.jimzhouzzy.klotski.server;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class GameWebSocketServer extends WebSocketServer {
    private final Set<WebSocket> connections = Collections.synchronizedSet(new HashSet<>());

    public GameWebSocketServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connections.add(conn);
        System.out.println("New socket connection: " + conn.getRemoteSocketAddress());
        System.out.println("Handshake resource descriptor: " + handshake.getResourceDescriptor());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        connections.remove(conn);
        System.out.println("Socket connection closed: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket server started on port " + getPort());
    }

    public void broadcastGameState(String gameState) {
        synchronized (connections) {
            for (WebSocket conn : connections) {
                conn.send(gameState);
            }
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("Message received:\n" + message);
        if (message.contains("boardState:")) {
            // boardState is user(1st line of the message) + the last 5 rows of the message string
            String[] lines = message.split("\n");
            StringBuilder boardState = new StringBuilder();
            boardState.append(lines[0]).append("\n");
            // append last 5 lines of the message
            for (int i = lines.length - 5; i < lines.length; i++) {
                boardState.append(lines[i]).append("\n");
            }

            broadcastGameState("Board state updated:\n" + boardState);
            // broadcast("Board state updated: " + boardState);
        }
    }

    public void close() {
        try {
            // Close all active WebSocket connections
            synchronized (connections) {
                for (WebSocket conn : connections) {
                    conn.close(1000, "Server shutting down"); // Close with normal closure code
                }
                connections.clear(); // Clear the connections set
            }
    
            // Stop the WebSocket server
            stop();
            System.out.println("WebSocket server stopped.");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error while closing WebSocket server: " + e.getMessage());
        }
    }
}