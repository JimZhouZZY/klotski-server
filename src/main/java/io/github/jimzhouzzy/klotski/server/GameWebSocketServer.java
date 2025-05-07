package io.github.jimzhouzzy.klotski.server;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GameWebSocketServer extends WebSocketServer {
    public final Map<WebSocket, String> userConnections = Collections.synchronizedMap(new HashMap<>());

    public GameWebSocketServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        userConnections.put(conn, null);
        System.out.println("New socket connection: " + conn.getRemoteSocketAddress());
        System.out.println("Handshake resource descriptor: " + handshake.getResourceDescriptor());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String username = userConnections.remove(conn);
        if (username != null) {
            System.out.println("User " + username + " disconnected.");
            broadcastOnlineUsers();
        }
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
        synchronized (userConnections) {
            for (WebSocket conn : userConnections.keySet()) {
                conn.send(gameState);
            }
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("Message received:\n" + message);
        if (message.startsWith("login:")) {
            String username = message.substring(6); // 提取用户名
            System.out.println("Login request from " + username);
            if (LoginServer.userDatabase.containsKey(username)) {
                setUser(conn, username);
                System.out.println("User " + username + " logged in.");
                conn.send("Login successful. Welcome, " + username + "!");
            } else {
                System.out.println("Invalid login attempt from " + username);
                conn.send("Error: Invalid username.");
            }
            return;
        }

        String username = getUsername(conn);
        System.out.println("Current user: " + username);
        if (username == null) {
            System.out.println("Message from unauthenticated user.");
            conn.send("Error: You must log in first.");
            return;
        }
        
        if (message.contains("GetOnlineUsers")) {
            String onlineUsers = "Online users: " + String.join(", ", userConnections.values());
            conn.send(onlineUsers);
            System.out.println(onlineUsers);
        }

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
        }
    }

    public void setUser(WebSocket conn, String username) {
        userConnections.put(conn, username);
        broadcastOnlineUsers();
    }

    public void broadcastOnlineUsers() {
        String onlineUsers = "Online users: " + String.join(", ", userConnections.values());
        System.out.println(onlineUsers);
    }

    public String getUsername(WebSocket conn) {
        return userConnections.get(conn);
    }

    public void close() {
        try {
            // Close all active WebSocket connections
            synchronized (userConnections) {
                for (WebSocket conn : userConnections.keySet()) {
                    conn.close(1000, "Server shutting down"); // Close with normal closure code
                }
                userConnections.clear(); // Clear the connections map
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